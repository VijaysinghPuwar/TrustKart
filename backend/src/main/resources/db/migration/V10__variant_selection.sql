-- Product variants (V9 adds product.options). A cart line is a product plus the chosen options, stored in
-- canonical form (every group filled in, defaults included), so "512 GB / Silver" and "256 GB / Silver" are
-- separate lines and re-adding the same configuration merges into one.
ALTER TABLE cart_item ADD COLUMN options JSONB NOT NULL DEFAULT '{}';
ALTER TABLE cart_item DROP CONSTRAINT cart_item_unique_product;
ALTER TABLE cart_item ADD CONSTRAINT cart_item_unique_selection UNIQUE (shopper_id, product_id, options);

-- Order lines snapshot the configuration as shown to the shopper, e.g. "512 GB · Silver".
ALTER TABLE virtual_purchase_item ADD COLUMN options_label VARCHAR(200);
