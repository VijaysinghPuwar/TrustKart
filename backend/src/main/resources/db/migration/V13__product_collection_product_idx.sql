-- A product page lists its collection tags (WHERE product_id = ?). The primary key is (tag, product_id), which
-- can't serve that lookup, so every product page scanned the whole table.
CREATE INDEX product_collection_product_idx ON product_collection (product_id);
