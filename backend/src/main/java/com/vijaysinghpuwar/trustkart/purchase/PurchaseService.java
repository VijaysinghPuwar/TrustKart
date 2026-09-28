package com.vijaysinghpuwar.trustkart.purchase;

import com.vijaysinghpuwar.trustkart.address.AddressService;
import com.vijaysinghpuwar.trustkart.cart.CartItem;
import com.vijaysinghpuwar.trustkart.cart.CartService;
import com.vijaysinghpuwar.trustkart.catalog.application.CatalogMapper;
import com.vijaysinghpuwar.trustkart.catalog.application.CatalogService;
import com.vijaysinghpuwar.trustkart.catalog.application.ProductSummary;
import com.vijaysinghpuwar.trustkart.catalog.domain.StockStatus;
import com.vijaysinghpuwar.trustkart.catalog.infra.InventoryRepository;
import com.vijaysinghpuwar.trustkart.common.error.ApiException;
import com.vijaysinghpuwar.trustkart.common.error.ErrorCode;
import com.vijaysinghpuwar.trustkart.common.error.NotFoundException;
import com.vijaysinghpuwar.trustkart.common.error.ValidationException;
import com.vijaysinghpuwar.trustkart.common.money.MoneyWire;
import com.vijaysinghpuwar.trustkart.purchase.PurchaseViews.ItemView;
import com.vijaysinghpuwar.trustkart.purchase.PurchaseViews.PurchasePage;
import com.vijaysinghpuwar.trustkart.purchase.PurchaseViews.PurchaseSummary;
import com.vijaysinghpuwar.trustkart.purchase.PurchaseViews.PurchaseView;
import com.vijaysinghpuwar.trustkart.purchase.PurchaseViews.Quote;
import com.vijaysinghpuwar.trustkart.purchase.PurchaseViews.QuoteLine;
import com.vijaysinghpuwar.trustkart.security.RateLimitPolicy;
import com.vijaysinghpuwar.trustkart.security.RateLimiter;
import com.vijaysinghpuwar.trustkart.security.Tokens;
import com.vijaysinghpuwar.trustkart.shopper.ShopperMergedEvent;
import com.vijaysinghpuwar.trustkart.wallet.VirtualWallet;
import com.vijaysinghpuwar.trustkart.wallet.WalletMode;
import com.vijaysinghpuwar.trustkart.wallet.WalletService;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Virtual checkout. Everything a shopper can influence is recomputed here: prices come from the catalog,
 * quantities are checked against live stock, and the total is the server's own sum. No real payment is
 * attempted anywhere; the "payment" is a debit on the virtual wallet ledger.
 */
@Service
public class PurchaseService {

    /** A single product bought directly from its page ("Instant Virtual Buy"), bypassing the cart. */
    public record InstantLine(long productId, int quantity) {}

    public record PlaceRequest(DeliveryPreset preset, SimulationAddress address, UUID addressId, String expectedTotal,
            InstantLine instant) {}

    private static final Pattern IDEMPOTENCY_KEY = Pattern.compile("[A-Za-z0-9-]{16,64}");
    private static final DateTimeFormatter ORDER_DATE = DateTimeFormatter.ofPattern("yyyyMMdd").withZone(ZoneOffset.UTC);

    private final CartService cart;
    private final CatalogService catalog;
    private final InventoryRepository inventory;
    private final WalletService wallets;
    private final VirtualPurchaseRepository purchases;
    private final RateLimiter rateLimiter;
    private final JdbcClient jdbc;
    private final AddressService addresses;
    private final Clock clock;

    public PurchaseService(CartService cart, CatalogService catalog, InventoryRepository inventory, WalletService wallets,
            VirtualPurchaseRepository purchases, RateLimiter rateLimiter, JdbcClient jdbc, AddressService addresses, Clock clock) {
        this.cart = cart;
        this.catalog = catalog;
        this.inventory = inventory;
        this.wallets = wallets;
        this.purchases = purchases;
        this.rateLimiter = rateLimiter;
        this.jdbc = jdbc;
        this.addresses = addresses;
        this.clock = clock;
    }

    /** Read-only preview priced by the server. Creates the wallet (with its starting balance) if needed. */
    @Transactional
    public Quote quote(long shopperId, InstantLine instant) {
        VirtualWallet wallet = wallets.lock(shopperId);
        List<Priced> lines = price(requestedLines(shopperId, instant));
        return toQuote(lines, wallet);
    }

    @Transactional
    public PurchaseView place(long shopperId, PlaceRequest request, String idempotencyKey) {
        if (idempotencyKey == null || !IDEMPOTENCY_KEY.matcher(idempotencyKey).matches()) {
            throw new ApiException(ErrorCode.IDEMPOTENCY_KEY_REQUIRED);
        }
        String requestHash = requestHash(request);
        var replay = replay(shopperId, idempotencyKey, requestHash);
        if (replay != null) {
            return replay;
        }
        rateLimiter.check(RateLimitPolicy.PURCHASE_PER_SHOPPER, Long.toString(shopperId));

        // 1. Serialise on the wallet row. A duplicate submit waits here, then finds the first attempt's result.
        VirtualWallet wallet = wallets.lock(shopperId);
        replay = replay(shopperId, idempotencyKey, requestHash);
        if (replay != null) {
            return replay;
        }

        // 2. Reprice everything from the catalog.
        List<CartItem> cartLines = request.instant() == null ? cart.activeLines(shopperId) : List.of();
        List<Priced> lines = price(requestedLines(shopperId, request.instant()));
        if (lines.isEmpty()) {
            throw new ApiException(ErrorCode.CART_EMPTY);
        }
        Quote quote = toQuote(lines, wallet);
        List<Priced> blocked = lines.stream().filter(l -> l.issue() != null).toList();
        if (!blocked.isEmpty()) {
            throw new ApiException(ErrorCode.OUT_OF_STOCK, ErrorCode.OUT_OF_STOCK.defaultMessage(), Map.of("quote", quote));
        }
        BigDecimal total = sum(lines);
        if (request.expectedTotal() != null && !sameAmount(request.expectedTotal(), total)) {
            // The client's number is never used as the amount; a mismatch only means the shopper saw stale prices.
            throw new ApiException(ErrorCode.PRICE_CHANGED, ErrorCode.PRICE_CHANGED.defaultMessage(), Map.of("quote", quote));
        }

        // 3. Budget mode: funds check against the locked balance.
        if (wallet.getMode() == WalletMode.BUDGET && wallet.getBalance().compareTo(total) < 0) {
            throw new ApiException(ErrorCode.INSUFFICIENT_VIRTUAL_FUNDS, "You need "
                    + usd(total.subtract(wallet.getBalance())) + " more virtual funds for this purchase.",
                    Map.of("shortfall", MoneyWire.format(total.subtract(wallet.getBalance())),
                            "balance", MoneyWire.format(wallet.getBalance()), "total", MoneyWire.format(total)));
        }

        // 4. Commit stock with conditional updates; any failure rolls back the whole purchase.
        List<VirtualPurchaseItem> items = new ArrayList<>();
        for (Priced line : lines) {
            ProductSummary p = line.product();
            int committed = line.quantity();
            if (!inventory.tryCommit(p.id(), line.quantity())) {
                if (p.stockStatus() != StockStatus.BACKORDER) {
                    throw new ApiException(ErrorCode.OUT_OF_STOCK, p.name() + " just sold out.", Map.of("productId", p.id()));
                }
                committed = 0;
            }
            items.add(new VirtualPurchaseItem(p.id(), p.slug(), p.name(), p.categoryName(),
                    p.image() == null ? null : p.image().small(), p.price(), line.quantity(), committed));
        }

        // 5. Record the purchase, debit the ledger, clear the cart.
        Instant now = clock.instant();
        VirtualPurchase purchase = new VirtualPurchase(nextOrderNumber(now), shopperId, items, wallet.getMode(),
                request.preset(), destination(shopperId, request), idempotencyKey, requestHash, now);
        if (wallet.getMode() == WalletMode.BUDGET) {
            BigDecimal before = wallet.getBalance();
            wallets.debit(wallet, total, purchase.getOrderNumber());
            purchase.recordBalances(before, wallet.getBalance());
        }
        purchases.save(purchase);
        if (request.instant() == null) {
            cart.removeLines(cartLines);
        }
        return toView(purchase);
    }

    /** Refunds restore the virtual balance (Budget-mode purchases) and stock. Idempotent: a repeat returns the result. */
    @Transactional
    public PurchaseView refund(long shopperId, UUID purchaseId) {
        VirtualWallet wallet = wallets.lock(shopperId);
        VirtualPurchase purchase = purchases.findByPublicIdAndShopperId(purchaseId, shopperId)
                .orElseThrow(() -> new NotFoundException("Purchase"));
        if (purchase.getStatus() == PurchaseStatus.REFUNDED) {
            return toView(purchase);
        }
        purchase.getItems().forEach(i -> {
            if (i.getStockCommitted() > 0) {
                inventory.release(i.getProductId(), i.getStockCommitted());
            }
        });
        if (purchase.getWalletMode() == WalletMode.BUDGET) {
            wallets.refund(wallet, purchase.getTotal(), purchase.getOrderNumber());
        }
        purchase.markRefunded(clock.instant());
        return toView(purchase);
    }

    @Transactional(readOnly = true)
    public PurchaseView get(long shopperId, UUID purchaseId) {
        return purchases.findByPublicIdAndShopperId(purchaseId, shopperId).map(this::toView)
                .orElseThrow(() -> new NotFoundException("Purchase"));
    }

    @Transactional(readOnly = true)
    public PurchasePage list(long shopperId, int page, int size) {
        Page<VirtualPurchase> result = purchases.findByShopperIdOrderByCreatedAtDesc(shopperId, PageRequest.of(page, size));
        List<PurchaseSummary> items = result.getContent().stream()
                .map(p -> new PurchaseSummary(p.getPublicId(), p.getOrderNumber(), p.getStatus().name(), p.getCreatedAt(),
                        p.getItemCount(), MoneyWire.format(p.getTotal()), p.getWalletMode().name(),
                        p.getItems().stream().map(VirtualPurchaseItem::getImageUrl).filter(Objects::nonNull).limit(4).toList()))
                .toList();
        return new PurchasePage(items, page, size, result.getTotalElements(), result.getTotalPages());
    }

    @EventListener
    @Transactional
    public void onShopperMerged(ShopperMergedEvent event) {
        jdbc.sql("UPDATE virtual_purchase SET shopper_id = :to WHERE shopper_id = :from")
                .param("to", event.toShopperId()).param("from", event.fromShopperId()).update();
    }

    /**
     * Resolves where the order "goes". A saved address is looked up by (id, shopper), so another shopper's
     * address id is a 404, never a leak. The result is a snapshot: editing the address later won't touch receipts.
     */
    private SimulationAddress destination(long shopperId, PlaceRequest request) {
        if (request.preset() != DeliveryPreset.ADDRESS) {
            return request.address();
        }
        if (request.addressId() != null) {
            AddressService.AddressView a = addresses.get(shopperId, request.addressId());
            return new SimulationAddress(a.label(), a.fullName(), a.line1(), a.line2(), a.city(), a.region(), a.postalCode(), a.country());
        }
        SimulationAddress inline = request.address();
        if (inline == null || isBlank(inline.fullName()) || isBlank(inline.line1()) || isBlank(inline.city())
                || isBlank(inline.postalCode()) || isBlank(inline.country())) {
            throw new ValidationException("simulationAddress", "Enter a name, street, city, postal code and country, or pick a saved address.");
        }
        return inline;
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private record Requested(long productId, int quantity) {}

    private record Priced(ProductSummary product, int quantity, BigDecimal lineTotal, String issue) {}

    private List<Requested> requestedLines(long shopperId, InstantLine instant) {
        if (instant != null) {
            return List.of(new Requested(instant.productId(), instant.quantity()));
        }
        return cart.activeLines(shopperId).stream().map(i -> new Requested(i.getProductId(), i.getQuantity())).toList();
    }

    private List<Priced> price(List<Requested> requested) {
        Map<Long, ProductSummary> products = catalog.summariesById(requested.stream().map(Requested::productId).toList());
        List<Priced> out = new ArrayList<>();
        for (Requested r : requested) {
            ProductSummary p = products.get(r.productId());
            if (p == null) {
                throw new NotFoundException("Product");
            }
            int max = CatalogMapper.maxQuantity(p.stockStatus(), p.sellableQuantity());
            String issue = !p.stockStatus().purchasable() ? p.stockStatus().name()
                    : r.quantity() > max ? "QUANTITY_EXCEEDS_STOCK" : null;
            out.add(new Priced(p, r.quantity(), p.price().multiply(BigDecimal.valueOf(r.quantity())), issue));
        }
        return out;
    }

    private Quote toQuote(List<Priced> lines, VirtualWallet wallet) {
        BigDecimal total = sum(lines);
        boolean budget = wallet.getMode() == WalletMode.BUDGET;
        BigDecimal after = budget ? wallet.getBalance().subtract(total) : wallet.getBalance();
        BigDecimal shortfall = budget && after.signum() < 0 ? after.negate() : null;
        boolean ok = !lines.isEmpty() && lines.stream().allMatch(l -> l.issue() == null) && shortfall == null;
        List<QuoteLine> quoteLines = lines.stream()
                .map(l -> new QuoteLine(l.product().id(), l.product().slug(), l.product().name(),
                        l.product().image() == null ? null : l.product().image().small(), MoneyWire.format(l.product().price()),
                        l.quantity(), MoneyWire.format(l.lineTotal()), l.issue()))
                .toList();
        return new Quote(quoteLines, lines.stream().mapToInt(Priced::quantity).sum(), MoneyWire.format(total), "0.00",
                MoneyWire.format(total), wallet.getMode().name(), MoneyWire.format(wallet.getBalance()),
                budget && shortfall == null ? MoneyWire.format(after) : null, MoneyWire.format(shortfall), ok, true);
    }

    private PurchaseView replay(long shopperId, String key, String requestHash) {
        return purchases.findByShopperIdAndIdempotencyKey(shopperId, key).map(existing -> {
            if (!existing.getRequestHash().equals(requestHash)) {
                throw new ApiException(ErrorCode.IDEMPOTENCY_KEY_REUSED);
            }
            return toView(existing);
        }).orElse(null);
    }

    private String nextOrderNumber(Instant now) {
        long n = jdbc.sql("SELECT nextval('virtual_purchase_number_seq')").query(Long.class).single();
        return "TK-" + ORDER_DATE.format(now) + "-" + String.format("%04d", n);
    }

    private static String requestHash(PlaceRequest r) {
        String canonical = String.join("|",
                String.valueOf(r.preset()),
                String.valueOf(r.addressId()),
                r.address() == null ? "" : r.address().toString(),
                r.instant() == null ? "cart" : r.instant().productId() + "x" + r.instant().quantity());
        return Tokens.sha256(canonical);
    }

    private static BigDecimal sum(List<Priced> lines) {
        return lines.stream().map(Priced::lineTotal).reduce(BigDecimal.ZERO, BigDecimal::add).setScale(MoneyWire.SCALE);
    }

    private static boolean sameAmount(String expected, BigDecimal actual) {
        try {
            return new BigDecimal(expected).compareTo(actual) == 0;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private static String usd(BigDecimal amount) {
        java.text.NumberFormat f = java.text.NumberFormat.getCurrencyInstance(java.util.Locale.US);
        return f.format(amount);
    }

    private PurchaseView toView(VirtualPurchase p) {
        List<ItemView> items = p.getItems().stream()
                .map(i -> new ItemView(i.getProductId(), i.getProductSlug(), i.getProductName(), i.getCategoryName(),
                        i.getImageUrl(), MoneyWire.format(i.getUnitPrice()), i.getQuantity(), MoneyWire.format(i.getLineTotal())))
                .toList();
        return new PurchaseView(p.getPublicId(), p.getOrderNumber(), p.getStatus().name(), p.getCreatedAt(), p.getRefundedAt(),
                p.getItemCount(), MoneyWire.format(p.getSubtotal()), MoneyWire.format(p.getShipping()), MoneyWire.format(p.getTotal()),
                p.getWalletMode().name(), MoneyWire.format(p.getBalanceBefore()), MoneyWire.format(p.getBalanceAfter()),
                p.getDeliveryPreset().name(), p.getSimulationAddress(), items, true);
    }
}
