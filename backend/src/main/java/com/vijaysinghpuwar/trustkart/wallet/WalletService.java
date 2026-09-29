package com.vijaysinghpuwar.trustkart.wallet;

import com.vijaysinghpuwar.trustkart.common.error.ApiException;
import com.vijaysinghpuwar.trustkart.common.error.ErrorCode;
import com.vijaysinghpuwar.trustkart.common.error.ValidationException;
import com.vijaysinghpuwar.trustkart.common.money.MoneyWire;
import com.vijaysinghpuwar.trustkart.security.RateLimitPolicy;
import com.vijaysinghpuwar.trustkart.security.RateLimiter;
import com.vijaysinghpuwar.trustkart.shopper.ShopperMergedEvent;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Virtual funds only. Nothing here touches a payment provider, card, bank or real currency. */
@Service
public class WalletService {

    public static final BigDecimal STARTING_BALANCE = new BigDecimal("100000.00");
    public static final BigDecimal MIN_CREDIT = new BigDecimal("1.00");
    public static final BigDecimal MAX_CREDIT = new BigDecimal("10000000.00");

    public record WalletView(String balance, WalletMode mode, boolean exists, String startingBalance) {}

    public record TransactionView(String id, String type, String amount, String balanceBefore, String balanceAfter,
            String reference, String description, Instant createdAt) {}

    public record TransactionPage(List<TransactionView> items, int page, int size, long totalItems) {}

    private final VirtualWalletRepository wallets;
    private final WalletLedger ledger;
    private final RateLimiter rateLimiter;
    private final JdbcClient jdbc;
    private final Clock clock;

    public WalletService(VirtualWalletRepository wallets, WalletLedger ledger, RateLimiter rateLimiter, JdbcClient jdbc, Clock clock) {
        this.wallets = wallets;
        this.ledger = ledger;
        this.rateLimiter = rateLimiter;
        this.jdbc = jdbc;
        this.clock = clock;
    }

    /** Read-only preview for shoppers who don't have a wallet yet: shows the starting balance without creating rows. */
    public static WalletView preview() {
        return new WalletView(MoneyWire.format(STARTING_BALANCE), WalletMode.BUDGET, false, MoneyWire.format(STARTING_BALANCE));
    }

    @Transactional
    public WalletView view(long shopperId) {
        return toView(current(shopperId));
    }

    /**
     * The shopper's wallet for reading: no row lock, so viewing a balance never waits behind (or blocks) a purchase.
     * Only a shopper's first visit takes the locked path, which creates the wallet and credits it exactly once.
     */
    @Transactional
    public VirtualWallet current(long shopperId) {
        return wallets.findByShopperId(shopperId).orElseGet(() -> lock(shopperId));
    }

    /**
     * Returns the shopper's wallet, locked FOR UPDATE, creating it with the starting virtual balance on first use.
     * The insert uses ON CONFLICT so two first requests racing can't create two wallets.
     */
    @Transactional
    public VirtualWallet lock(long shopperId) {
        Instant now = clock.instant();
        int created = jdbc.sql("""
                        INSERT INTO virtual_wallet (shopper_id, balance, mode, created_at, updated_at)
                        VALUES (:s, 0, 'BUDGET', :now, :now) ON CONFLICT (shopper_id) DO NOTHING""")
                .param("s", shopperId).param("now", java.sql.Timestamp.from(now)).update();
        VirtualWallet wallet = wallets.findByShopperIdForUpdate(shopperId).orElseThrow();
        if (created == 1) {
            apply(wallet, WalletLedger.Type.CREDIT, STARTING_BALANCE, null, "Starting virtual balance", null);
        }
        return wallet;
    }

    @Transactional
    public WalletView credit(long shopperId, BigDecimal amount, String idempotencyKey) {
        if (amount.scale() > MoneyWire.SCALE || amount.compareTo(MIN_CREDIT) < 0 || amount.compareTo(MAX_CREDIT) > 0) {
            throw new ValidationException("amount", "Enter an amount from $1.00 to $10,000,000.00 with at most 2 decimal places.");
        }
        rateLimiter.check(RateLimitPolicy.WALLET_CREDIT_PER_SHOPPER, Long.toString(shopperId));
        VirtualWallet wallet = lock(shopperId);
        if (ledger.findByKey(wallet.getId(), idempotencyKey).isPresent()) {
            return toView(wallet);
        }
        if (wallet.getBalance().add(amount).compareTo(VirtualWallet.MAX_BALANCE) > 0) {
            throw new ApiException(ErrorCode.WALLET_LIMIT, ErrorCode.WALLET_LIMIT.defaultMessage(),
                    Map.of("maxBalance", MoneyWire.format(VirtualWallet.MAX_BALANCE)));
        }
        apply(wallet, WalletLedger.Type.CREDIT, amount, null, "Added virtual funds", idempotencyKey);
        return toView(wallet);
    }

    @Transactional
    public WalletView reset(long shopperId) {
        VirtualWallet wallet = lock(shopperId);
        BigDecimal delta = STARTING_BALANCE.subtract(wallet.getBalance());
        if (delta.signum() != 0) {
            apply(wallet, WalletLedger.Type.RESET, delta, null, "Wallet reset to starting balance", null);
        }
        return toView(wallet);
    }

    @Transactional
    public WalletView setMode(long shopperId, WalletMode mode) {
        VirtualWallet wallet = lock(shopperId);
        wallet.setMode(mode, clock.instant());
        return toView(wallet);
    }

    @Transactional
    public TransactionPage transactions(long shopperId, int page, int size) {
        VirtualWallet wallet = current(shopperId);
        List<TransactionView> items = ledger.page(wallet.getId(), page, size).stream()
                .map(e -> new TransactionView(e.id().toString(), e.type().name(), MoneyWire.format(e.amount()),
                        MoneyWire.format(e.balanceBefore()), MoneyWire.format(e.balanceAfter()), e.reference(),
                        e.description(), e.createdAt()))
                .toList();
        return new TransactionPage(items, page, size, ledger.count(wallet.getId()));
    }

    /** Debits a locked wallet for a purchase. Caller must hold the lock from {@link #lock(long)}. */
    public void debit(VirtualWallet wallet, BigDecimal amount, String reference) {
        apply(wallet, WalletLedger.Type.PURCHASE, amount.negate(), reference, "Virtual purchase " + reference, null);
    }

    /** Returns funds from a refunded purchase to a locked wallet. */
    public void refund(VirtualWallet wallet, BigDecimal amount, String reference) {
        if (wallet.getBalance().add(amount).compareTo(VirtualWallet.MAX_BALANCE) > 0) {
            throw new ApiException(ErrorCode.WALLET_LIMIT);
        }
        apply(wallet, WalletLedger.Type.REFUND, amount, reference, "Refund of virtual purchase " + reference, null);
    }

    /**
     * A guest's purchases move to the account (handled by the purchase module). The guest wallet's balance
     * is virtual and is not added on top of the account's; if the account has no wallet yet, the guest wallet
     * simply becomes the account's.
     */
    @EventListener
    @Transactional
    public void onShopperMerged(ShopperMergedEvent event) {
        boolean accountHasWallet = wallets.findByShopperId(event.toShopperId()).isPresent();
        if (!accountHasWallet) {
            jdbc.sql("UPDATE virtual_wallet SET shopper_id = :to WHERE shopper_id = :from")
                    .param("to", event.toShopperId()).param("from", event.fromShopperId()).update();
        }
    }

    private void apply(VirtualWallet wallet, WalletLedger.Type type, BigDecimal delta, String reference, String description,
            String idempotencyKey) {
        BigDecimal before = wallet.getBalance();
        BigDecimal after = wallet.move(delta, clock.instant());
        ledger.append(wallet.getId(), type, before, after, reference, description, idempotencyKey);
    }

    private static WalletView toView(VirtualWallet wallet) {
        return new WalletView(MoneyWire.format(wallet.getBalance()), wallet.getMode(), true, MoneyWire.format(STARTING_BALANCE));
    }
}
