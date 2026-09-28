-- Selectable purchase options (storage, colour, configuration...). Ordered groups; at most one group carries
-- prices, and a price is the absolute unit price for that choice. The default choice of the priced group always
-- equals product.price, so listings keep showing the base price. Validated by ProductOptions before being stored.
ALTER TABLE product ADD COLUMN options JSONB NOT NULL DEFAULT '[]'::jsonb;
ALTER TABLE product ADD CONSTRAINT product_options_array CHECK (jsonb_typeof(options) = 'array');
