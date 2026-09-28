-- RENDER: a TrustKart-made studio illustration, used where no clean, licensed photograph of the product exists.
ALTER TABLE product_image DROP CONSTRAINT product_image_match;
ALTER TABLE product_image ADD CONSTRAINT product_image_match
    CHECK (match_type IN ('EXACT', 'PRODUCT_LINE', 'REPRESENTATIVE', 'RENDER'));
