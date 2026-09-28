package com.vijaysinghpuwar.trustkart.address;

import com.vijaysinghpuwar.trustkart.common.error.ApiException;
import com.vijaysinghpuwar.trustkart.common.error.ErrorCode;
import com.vijaysinghpuwar.trustkart.common.error.NotFoundException;
import com.vijaysinghpuwar.trustkart.shopper.ShopperMergedEvent;
import java.util.List;
import java.util.UUID;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Address book. Every statement is scoped to the owning shopper, so foreign ids behave as missing (404). */
@Service
public class AddressService {

    public static final int MAX_ADDRESSES = 10;

    public record AddressView(UUID id, String label, String fullName, String line1, String line2, String city, String region,
            String postalCode, String country, boolean isDefault) {

        public AddressInput toInput() {
            return new AddressInput(label, fullName, line1, line2, city, region, postalCode, country);
        }
    }

    private static final String SELECT = """
            SELECT id, label, full_name, line1, line2, city, region, postal_code, country, is_default
            FROM shopper_address""";

    private final JdbcClient jdbc;

    public AddressService(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public List<AddressView> list(long shopperId) {
        return jdbc.sql(SELECT + " WHERE shopper_id = :s ORDER BY is_default DESC, created_at")
                .param("s", shopperId).query(AddressService::map).list();
    }

    @Transactional(readOnly = true)
    public AddressView get(long shopperId, UUID id) {
        return jdbc.sql(SELECT + " WHERE id = :id AND shopper_id = :s").param("id", id).param("s", shopperId)
                .query(AddressService::map).optional().orElseThrow(() -> new NotFoundException("Address"));
    }

    @Transactional
    public AddressView create(long shopperId, AddressInput raw, boolean makeDefault) {
        AddressInput in = raw.normalized();
        long count = jdbc.sql("SELECT count(*) FROM shopper_address WHERE shopper_id = :s").param("s", shopperId).query(Long.class).single();
        if (count >= MAX_ADDRESSES) {
            throw new ApiException(ErrorCode.CONFLICT, "You can save up to " + MAX_ADDRESSES + " addresses.");
        }
        UUID id = UUID.randomUUID();
        boolean isDefault = makeDefault || count == 0;
        if (isDefault) {
            clearDefault(shopperId);
        }
        jdbc.sql("""
                        INSERT INTO shopper_address (id, shopper_id, label, full_name, line1, line2, city, region, postal_code, country, is_default)
                        VALUES (:id, :s, :label, :name, :l1, :l2, :city, :region, :zip, :country, :def)""")
                .param("id", id).param("s", shopperId).param("label", in.label()).param("name", in.fullName())
                .param("l1", in.line1()).param("l2", in.line2()).param("city", in.city()).param("region", in.region())
                .param("zip", in.postalCode()).param("country", in.country()).param("def", isDefault)
                .update();
        return get(shopperId, id);
    }

    @Transactional
    public AddressView update(long shopperId, UUID id, AddressInput raw) {
        AddressInput in = raw.normalized();
        get(shopperId, id);
        jdbc.sql("""
                        UPDATE shopper_address SET label = :label, full_name = :name, line1 = :l1, line2 = :l2, city = :city,
                               region = :region, postal_code = :zip, country = :country, updated_at = now()
                        WHERE id = :id AND shopper_id = :s""")
                .param("id", id).param("s", shopperId).param("label", in.label()).param("name", in.fullName())
                .param("l1", in.line1()).param("l2", in.line2()).param("city", in.city()).param("region", in.region())
                .param("zip", in.postalCode()).param("country", in.country())
                .update();
        return get(shopperId, id);
    }

    @Transactional
    public void delete(long shopperId, UUID id) {
        AddressView existing = get(shopperId, id);
        jdbc.sql("DELETE FROM shopper_address WHERE id = :id AND shopper_id = :s").param("id", id).param("s", shopperId).update();
        if (existing.isDefault()) {
            // Promote the oldest remaining address so there's always a default when any exist.
            jdbc.sql("""
                            UPDATE shopper_address SET is_default = TRUE WHERE id = (
                                SELECT id FROM shopper_address WHERE shopper_id = :s ORDER BY created_at LIMIT 1)""")
                    .param("s", shopperId).update();
        }
    }

    @Transactional
    public AddressView makeDefault(long shopperId, UUID id) {
        get(shopperId, id);
        clearDefault(shopperId);
        jdbc.sql("UPDATE shopper_address SET is_default = TRUE WHERE id = :id AND shopper_id = :s").param("id", id).param("s", shopperId).update();
        return get(shopperId, id);
    }

    /** Guest addresses move to the account; the account's default wins if it already has one. */
    @EventListener
    @Transactional
    public void onShopperMerged(ShopperMergedEvent event) {
        boolean accountHasDefault = jdbc.sql("SELECT count(*) FROM shopper_address WHERE shopper_id = :s AND is_default")
                .param("s", event.toShopperId()).query(Long.class).single() > 0;
        if (accountHasDefault) {
            jdbc.sql("UPDATE shopper_address SET is_default = FALSE WHERE shopper_id = :s").param("s", event.fromShopperId()).update();
        }
        jdbc.sql("UPDATE shopper_address SET shopper_id = :to WHERE shopper_id = :from")
                .param("to", event.toShopperId()).param("from", event.fromShopperId()).update();
    }

    private void clearDefault(long shopperId) {
        jdbc.sql("UPDATE shopper_address SET is_default = FALSE WHERE shopper_id = :s AND is_default").param("s", shopperId).update();
    }

    private static AddressView map(java.sql.ResultSet rs, int i) throws java.sql.SQLException {
        return new AddressView(rs.getObject("id", UUID.class), rs.getString("label"), rs.getString("full_name"), rs.getString("line1"),
                rs.getString("line2"), rs.getString("city"), rs.getString("region"), rs.getString("postal_code"),
                rs.getString("country"), rs.getBoolean("is_default"));
    }
}
