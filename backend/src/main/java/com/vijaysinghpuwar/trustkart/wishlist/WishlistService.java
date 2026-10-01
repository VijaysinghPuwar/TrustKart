package com.vijaysinghpuwar.trustkart.wishlist;

import com.vijaysinghpuwar.trustkart.catalog.application.CatalogService;
import com.vijaysinghpuwar.trustkart.catalog.application.CatalogViews.ProductCardDto;
import com.vijaysinghpuwar.trustkart.common.error.ApiException;
import com.vijaysinghpuwar.trustkart.common.error.ErrorCode;
import com.vijaysinghpuwar.trustkart.common.error.NotFoundException;
import com.vijaysinghpuwar.trustkart.shopper.ShopperMergedEvent;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.context.event.EventListener;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Named wishlists ("Dream Homelab", "Things I want"). The heart on a product card toggles the default list.
 * Every query is scoped by shopper, so list and item ids from other shoppers simply don't match.
 */
@Service
public class WishlistService {

    public static final String DEFAULT_NAME = "Things I want";
    public static final int MAX_LISTS = 20;
    public static final int MAX_ITEMS_PER_LIST = 100;

    public record ListView(UUID id, String name, boolean isDefault, List<ProductCardDto> items) {}

    private final JdbcClient jdbc;
    private final CatalogService catalog;

    public WishlistService(JdbcClient jdbc, CatalogService catalog) {
        this.jdbc = jdbc;
        this.catalog = catalog;
    }

    @Transactional(readOnly = true)
    public List<ListView> lists(long shopperId) {
        record Row(UUID id, String name, boolean isDefault) {}
        List<Row> rows = jdbc.sql("SELECT id, name, is_default FROM wishlist WHERE shopper_id = :s ORDER BY is_default DESC, created_at")
                .param("s", shopperId)
                .query((rs, i) -> new Row(rs.getObject("id", UUID.class), rs.getString("name"), rs.getBoolean("is_default")))
                .list();
        if (rows.isEmpty()) {
            return List.of();
        }
        // Every list's items in one query and every product card in one more, however many lists there are.
        record Item(UUID listId, long productId) {}
        List<Item> items = jdbc.sql("""
                        SELECT wi.wishlist_id, wi.product_id FROM wishlist_item wi JOIN wishlist w ON w.id = wi.wishlist_id
                        WHERE w.shopper_id = :s ORDER BY wi.added_at DESC""")
                .param("s", shopperId)
                .query((rs, i) -> new Item(rs.getObject("wishlist_id", UUID.class), rs.getLong("product_id")))
                .list();
        Map<Long, ProductCardDto> cards = catalog.lookupAll(items.stream().map(Item::productId).toList()).stream()
                .collect(Collectors.toMap(ProductCardDto::id, c -> c));
        Map<UUID, List<ProductCardDto>> byList = new HashMap<>();
        for (Item item : items) {
            ProductCardDto card = cards.get(item.productId());
            if (card != null) { // drafted or removed products drop out, as they did before
                byList.computeIfAbsent(item.listId(), k -> new ArrayList<>()).add(card);
            }
        }
        return rows.stream()
                .map(r -> new ListView(r.id(), r.name(), r.isDefault(), List.copyOf(byList.getOrDefault(r.id(), List.of()))))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<Long> savedProductIds(long shopperId) {
        return jdbc.sql("""
                        SELECT DISTINCT wi.product_id FROM wishlist_item wi JOIN wishlist w ON w.id = wi.wishlist_id
                        WHERE w.shopper_id = :s""")
                .param("s", shopperId).query(Long.class).list();
    }

    @Transactional
    public UUID createList(long shopperId, String name) {
        lockShopper(shopperId);
        long count = jdbc.sql("SELECT count(*) FROM wishlist WHERE shopper_id = :s").param("s", shopperId).query(Long.class).single();
        if (count >= MAX_LISTS) {
            throw new ApiException(ErrorCode.CONFLICT, "You can have up to " + MAX_LISTS + " lists.");
        }
        UUID id = UUID.randomUUID();
        try {
            jdbc.sql("INSERT INTO wishlist (id, shopper_id, name, is_default) VALUES (:id, :s, :n, :d)")
                    .param("id", id).param("s", shopperId).param("n", name.strip()).param("d", count == 0).update();
        } catch (DuplicateKeyException e) {
            throw new ApiException(ErrorCode.CONFLICT, "You already have a list with that name.");
        }
        return id;
    }

    @Transactional
    public void renameList(long shopperId, UUID listId, String name) {
        requireList(shopperId, listId);
        try {
            jdbc.sql("UPDATE wishlist SET name = :n WHERE id = :id").param("n", name.strip()).param("id", listId).update();
        } catch (DuplicateKeyException e) {
            throw new ApiException(ErrorCode.CONFLICT, "You already have a list with that name.");
        }
    }

    @Transactional
    public void deleteList(long shopperId, UUID listId) {
        requireList(shopperId, listId);
        jdbc.sql("DELETE FROM wishlist WHERE id = :id").param("id", listId).update();
    }

    /** Adds to the given list, or the default list (created on first use). Adding twice is a no-op. */
    @Transactional
    public void add(long shopperId, long productId, UUID listId) {
        if (catalog.summariesById(List.of(productId)).isEmpty()) {
            throw new NotFoundException("Product");
        }
        lockShopper(shopperId);
        UUID target = listId != null ? requireList(shopperId, listId) : defaultList(shopperId);
        long size = jdbc.sql("SELECT count(*) FROM wishlist_item WHERE wishlist_id = :w").param("w", target).query(Long.class).single();
        if (size >= MAX_ITEMS_PER_LIST) {
            throw new ApiException(ErrorCode.CONFLICT, "A list can hold up to " + MAX_ITEMS_PER_LIST + " products.");
        }
        jdbc.sql("INSERT INTO wishlist_item (wishlist_id, product_id) VALUES (:w, :p) ON CONFLICT DO NOTHING")
                .param("w", target).param("p", productId).update();
    }

    /** Removes the product from one list, or from every list when {@code listId} is null (the heart toggle). */
    @Transactional
    public void remove(long shopperId, long productId, UUID listId) {
        if (listId != null) {
            requireList(shopperId, listId);
        }
        jdbc.sql("""
                        DELETE FROM wishlist_item wi USING wishlist w
                        WHERE wi.wishlist_id = w.id AND w.shopper_id = :s AND wi.product_id = :p
                          AND (CAST(:w AS uuid) IS NULL OR w.id = CAST(:w AS uuid))""")
                .param("s", shopperId).param("p", productId).param("w", listId).update();
    }

    @Transactional(readOnly = true)
    public List<Long> productIds(long shopperId, UUID listId) {
        requireList(shopperId, listId);
        return productIds(listId);
    }

    @EventListener
    @Transactional
    public void onShopperMerged(ShopperMergedEvent event) {
        // Items from each guest list go into the account's list of the same name (created if needed).
        record Row(UUID id, String name) {}
        List<Row> guestLists = jdbc.sql("SELECT id, name FROM wishlist WHERE shopper_id = :s")
                .param("s", event.fromShopperId()).query((rs, i) -> new Row(rs.getObject("id", UUID.class), rs.getString("name"))).list();
        for (Row list : guestLists) {
            UUID target = jdbc.sql("SELECT id FROM wishlist WHERE shopper_id = :s AND name = :n")
                    .param("s", event.toShopperId()).param("n", list.name()).query(UUID.class).optional()
                    .orElseGet(() -> createList(event.toShopperId(), list.name()));
            jdbc.sql("""
                            INSERT INTO wishlist_item (wishlist_id, product_id, added_at)
                            SELECT :t, product_id, added_at FROM wishlist_item WHERE wishlist_id = :g ON CONFLICT DO NOTHING""")
                    .param("t", target).param("g", list.id()).update();
        }
    }

    private List<Long> productIds(UUID listId) {
        return jdbc.sql("SELECT product_id FROM wishlist_item WHERE wishlist_id = :w ORDER BY added_at DESC")
                .param("w", listId).query(Long.class).list();
    }

    /**
     * Serializes a shopper's list changes. Several hearts tapped at once each found no default list, each created one
     * and all but the first failed on its unique index; the list and item limits were also checked racily.
     */
    private void lockShopper(long shopperId) {
        jdbc.sql("SELECT id FROM shopper WHERE id = :s FOR UPDATE").param("s", shopperId).query(Long.class).optional();
    }

    private UUID requireList(long shopperId, UUID listId) {
        return jdbc.sql("SELECT id FROM wishlist WHERE id = :id AND shopper_id = :s")
                .param("id", listId).param("s", shopperId).query(UUID.class).optional()
                .orElseThrow(() -> new NotFoundException("Wishlist"));
    }

    private UUID defaultList(long shopperId) {
        return jdbc.sql("SELECT id FROM wishlist WHERE shopper_id = :s AND is_default").param("s", shopperId)
                .query(UUID.class).optional().orElseGet(() -> {
                    UUID id = UUID.randomUUID();
                    jdbc.sql("INSERT INTO wishlist (id, shopper_id, name, is_default) VALUES (:id, :s, :n, TRUE)")
                            .param("id", id).param("s", shopperId).param("n", DEFAULT_NAME).update();
                    return id;
                });
    }
}
