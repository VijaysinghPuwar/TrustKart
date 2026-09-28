package com.vijaysinghpuwar.trustkart.wallet;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Append-only virtual ledger. Rows are inserted, never updated or deleted by application code. */
@Repository
public class WalletLedger {

    public enum Type { CREDIT, PURCHASE, REFUND, RESET }

    public record Entry(UUID id, Type type, BigDecimal amount, BigDecimal balanceBefore, BigDecimal balanceAfter,
            String reference, String description, Instant createdAt) {}

    private final JdbcClient jdbc;

    public WalletLedger(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public void append(long walletId, Type type, BigDecimal before, BigDecimal after, String reference, String description,
            String idempotencyKey) {
        jdbc.sql("""
                        INSERT INTO virtual_transaction (public_id, wallet_id, type, amount, balance_before, balance_after,
                                                         reference, description, idempotency_key)
                        VALUES (:id, :w, :t, :amount, :before, :after, :ref, :d, :k)""")
                .param("id", UUID.randomUUID()).param("w", walletId).param("t", type.name())
                .param("amount", after.subtract(before)).param("before", before).param("after", after)
                .param("ref", reference).param("d", description).param("k", idempotencyKey)
                .update();
    }

    public Optional<Entry> findByKey(long walletId, String idempotencyKey) {
        return jdbc.sql(SELECT + " WHERE wallet_id = :w AND idempotency_key = :k")
                .param("w", walletId).param("k", idempotencyKey).query(WalletLedger::map).optional();
    }

    public List<Entry> page(long walletId, int page, int size) {
        return jdbc.sql(SELECT + " WHERE wallet_id = :w ORDER BY created_at DESC, id DESC LIMIT :n OFFSET :o")
                .param("w", walletId).param("n", size).param("o", (long) page * size).query(WalletLedger::map).list();
    }

    public long count(long walletId) {
        return jdbc.sql("SELECT count(*) FROM virtual_transaction WHERE wallet_id = :w").param("w", walletId).query(Long.class).single();
    }

    public BigDecimal totalCredited(long walletId) {
        return jdbc.sql("SELECT coalesce(sum(amount), 0) FROM virtual_transaction WHERE wallet_id = :w AND type = 'CREDIT'")
                .param("w", walletId).query(BigDecimal.class).single();
    }

    private static final String SELECT = """
            SELECT public_id, type, amount, balance_before, balance_after, reference, description, created_at
            FROM virtual_transaction""";

    private static Entry map(java.sql.ResultSet rs, int i) throws java.sql.SQLException {
        return new Entry(rs.getObject("public_id", UUID.class), Type.valueOf(rs.getString("type")), rs.getBigDecimal("amount"),
                rs.getBigDecimal("balance_before"), rs.getBigDecimal("balance_after"), rs.getString("reference"),
                rs.getString("description"), rs.getTimestamp("created_at").toInstant());
    }
}
