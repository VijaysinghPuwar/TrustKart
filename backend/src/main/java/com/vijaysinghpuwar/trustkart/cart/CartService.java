package com.vijaysinghpuwar.trustkart.cart;

import com.vijaysinghpuwar.trustkart.catalog.application.CatalogMapper;
import com.vijaysinghpuwar.trustkart.catalog.application.CatalogService;
import com.vijaysinghpuwar.trustkart.catalog.application.ProductSummary;
import com.vijaysinghpuwar.trustkart.common.error.ApiException;
import com.vijaysinghpuwar.trustkart.common.error.ErrorCode;
import com.vijaysinghpuwar.trustkart.common.error.NotFoundException;
import com.vijaysinghpuwar.trustkart.common.money.MoneyWire;
import com.vijaysinghpuwar.trustkart.shopper.ShopperMergedEvent;
import java.math.BigDecimal;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CartService {

    public static final int MAX_LINES = 50;

    private final CartItemRepository items;
    private final CatalogService catalog;
    private final Clock clock;

    public CartService(CartItemRepository items, CatalogService catalog, Clock clock) {
        this.items = items;
        this.catalog = catalog;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public CartView view(long shopperId) {
        List<CartItem> all = items.findByShopperIdOrderByAddedAtAsc(shopperId);
        Map<Long, ProductSummary> products = catalog.summariesById(all.stream().map(CartItem::getProductId).toList());
        List<CartView.Line> active = new ArrayList<>();
        List<CartView.Line> saved = new ArrayList<>();
        BigDecimal subtotal = BigDecimal.ZERO;
        int count = 0;
        for (CartItem item : all) {
            ProductSummary p = products.get(item.getProductId());
            if (p == null) {
                continue;
            }
            String issue = issue(p, item.getQuantity());
            BigDecimal unit = p.price();
            String label = null;
            var resolved = catalog.resolveOptionsIfValid(p.id(), item.getOptions());
            if (resolved.isPresent()) {
                unit = resolved.get().unitPrice();
                label = resolved.get().label();
            } else {
                // The configuration was retired after it was added; the shopper must pick again.
                issue = "OPTION_UNAVAILABLE";
            }
            BigDecimal line = unit.multiply(BigDecimal.valueOf(item.getQuantity()));
            CartView.Line view = new CartView.Line(item.getId(), CatalogMapper.card(p), item.getOptions(), label,
                    item.getQuantity(), MoneyWire.format(unit), MoneyWire.format(line), issue);
            if (item.isSavedForLater()) {
                saved.add(view);
            } else {
                active.add(view);
                if (issue == null) {
                    subtotal = subtotal.add(line);
                    count += item.getQuantity();
                }
            }
        }
        return new CartView(active, saved, MoneyWire.format(subtotal), count);
    }

    /**
     * Adds to the line with the same product and configuration if present. Quantities are validated against live
     * stock and the selection against the product's options (unknown groups or values are a 400); prices are never
     * taken from the client.
     */
    @Transactional
    public CartView add(long shopperId, long productId, Map<String, String> options, int quantity) {
        ProductSummary product = requirePurchasable(productId);
        Map<String, String> selection = new TreeMap<>(catalog.resolveOptions(productId, options).selection());
        var existing = items.findByShopperIdAndProductId(shopperId, productId).stream()
                .filter(i -> i.sameSelection(productId, selection)).findFirst();
        int newQuantity = existing.map(CartItem::getQuantity).orElse(0) + quantity;
        checkQuantity(product, newQuantity);
        if (existing.isPresent()) {
            existing.get().setQuantity(newQuantity, clock.instant());
            existing.get().setSavedForLater(false, clock.instant());
        } else {
            if (items.countByShopperId(shopperId) >= MAX_LINES) {
                throw new ApiException(ErrorCode.CONFLICT, "Your cart can hold up to " + MAX_LINES + " different products.");
            }
            items.save(new CartItem(shopperId, productId, selection, quantity, clock.instant()));
        }
        return view(shopperId);
    }

    @Transactional
    public CartView update(long shopperId, UUID itemId, Integer quantity, Boolean savedForLater) {
        CartItem item = items.findByIdAndShopperId(itemId, shopperId).orElseThrow(() -> new NotFoundException("Cart item"));
        if (quantity != null) {
            ProductSummary product = catalog.summariesById(List.of(item.getProductId())).get(item.getProductId());
            if (product != null) {
                checkQuantity(product, quantity);
            }
            item.setQuantity(quantity, clock.instant());
        }
        if (savedForLater != null) {
            item.setSavedForLater(savedForLater, clock.instant());
        }
        return view(shopperId);
    }

    @Transactional
    public CartView remove(long shopperId, UUID itemId) {
        CartItem item = items.findByIdAndShopperId(itemId, shopperId).orElseThrow(() -> new NotFoundException("Cart item"));
        items.delete(item);
        return view(shopperId);
    }

    @Transactional(readOnly = true)
    public long productIdOf(long shopperId, UUID itemId) {
        return items.findByIdAndShopperId(itemId, shopperId).orElseThrow(() -> new NotFoundException("Cart item")).getProductId();
    }

    /** Active (not saved-for-later) lines for checkout. */
    @Transactional(readOnly = true)
    public List<CartItem> activeLines(long shopperId) {
        return items.findByShopperIdOrderByAddedAtAsc(shopperId).stream().filter(i -> !i.isSavedForLater()).toList();
    }

    /** Called inside the purchase transaction after the order is recorded. */
    @Transactional
    public void removeLines(List<CartItem> lines) {
        items.deleteAll(lines);
    }

    /** Guest cart folded into the account on sign-in: quantities add up, capped at what can be bought. */
    @EventListener
    @Transactional
    public void onShopperMerged(ShopperMergedEvent event) {
        for (CartItem guestItem : items.findByShopperIdOrderByAddedAtAsc(event.fromShopperId())) {
            var target = items.findByShopperIdAndProductId(event.toShopperId(), guestItem.getProductId()).stream()
                    .filter(i -> i.sameSelection(guestItem.getProductId(), guestItem.getOptions())).findFirst();
            int combined = Math.min(CatalogMapper.MAX_QUANTITY_PER_LINE,
                    guestItem.getQuantity() + target.map(CartItem::getQuantity).orElse(0));
            if (target.isPresent()) {
                target.get().setQuantity(combined, clock.instant());
            } else {
                CartItem moved = new CartItem(event.toShopperId(), guestItem.getProductId(), guestItem.getOptions(), combined,
                        clock.instant());
                moved.setSavedForLater(guestItem.isSavedForLater(), clock.instant());
                items.save(moved);
            }
            items.delete(guestItem);
        }
    }

    private ProductSummary requirePurchasable(long productId) {
        ProductSummary product = catalog.summariesById(List.of(productId)).get(productId);
        if (product == null) {
            throw new NotFoundException("Product");
        }
        if (!product.stockStatus().purchasable()) {
            throw new ApiException(ErrorCode.PRODUCT_UNAVAILABLE);
        }
        return product;
    }

    private static void checkQuantity(ProductSummary product, int quantity) {
        int max = CatalogMapper.maxQuantity(product.stockStatus(), product.sellableQuantity());
        if (quantity < 1 || quantity > max) {
            throw new ApiException(ErrorCode.OUT_OF_STOCK,
                    max == 0 ? "This product is out of stock." : "You can add up to " + max + " of this product.",
                    Map.of("maxQuantity", max));
        }
    }

    private static String issue(ProductSummary p, int quantity) {
        return switch (p.stockStatus()) {
            case DISCONTINUED -> "DISCONTINUED";
            case OUT_OF_STOCK -> "OUT_OF_STOCK";
            default -> quantity > CatalogMapper.maxQuantity(p.stockStatus(), p.sellableQuantity()) ? "QUANTITY_EXCEEDS_STOCK" : null;
        };
    }
}
