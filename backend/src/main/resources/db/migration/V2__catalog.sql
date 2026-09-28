-- Catalog: categories, brands, products with JSONB specifications, images, inventory.
-- Money is NUMERIC(19,2) everywhere and maps to BigDecimal. Floating point is never used for prices.

CREATE TABLE category (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    slug        VARCHAR(80)  NOT NULL UNIQUE,
    name        VARCHAR(120) NOT NULL,
    parent_id   BIGINT REFERENCES category (id),
    description VARCHAR(500),
    sort_order  INT          NOT NULL DEFAULT 0,
    CONSTRAINT category_slug_format CHECK (slug ~ '^[a-z0-9]+(-[a-z0-9]+)*$')
);
CREATE INDEX category_parent_idx ON category (parent_id);

CREATE TABLE brand (
    id   BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    slug VARCHAR(80)  NOT NULL UNIQUE,
    name VARCHAR(120) NOT NULL UNIQUE
);

-- Registry of the specifications a category's products carry. It drives filter facets, labels, units and
-- comparison rows, and whitelists which JSONB keys may ever be used in a query. Definitions are inherited:
-- a product's effective definitions are those of its category and every ancestor.
CREATE TABLE spec_definition (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    category_id BIGINT       NOT NULL REFERENCES category (id),
    key         VARCHAR(60)  NOT NULL,
    label       VARCHAR(120) NOT NULL,
    data_type   VARCHAR(10)  NOT NULL,
    unit        VARCHAR(20),
    group_label VARCHAR(60)  NOT NULL DEFAULT 'Specifications',
    filterable  BOOLEAN      NOT NULL DEFAULT FALSE,
    comparable  BOOLEAN      NOT NULL DEFAULT TRUE,
    sort_order  INT          NOT NULL DEFAULT 0,
    CONSTRAINT spec_definition_key_format CHECK (key ~ '^[a-z][a-zA-Z0-9]*$'),
    CONSTRAINT spec_definition_type CHECK (data_type IN ('TEXT', 'NUMBER', 'BOOLEAN')),
    CONSTRAINT spec_definition_unique UNIQUE (category_id, key)
);

CREATE TABLE product (
    id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    sku              VARCHAR(40)   NOT NULL UNIQUE,
    slug             VARCHAR(120)  NOT NULL UNIQUE,
    name             VARCHAR(200)  NOT NULL,
    brand_id         BIGINT        NOT NULL REFERENCES brand (id),
    category_id      BIGINT        NOT NULL REFERENCES category (id),
    summary          VARCHAR(300)  NOT NULL,
    description      TEXT          NOT NULL,
    price            NUMERIC(19, 2) NOT NULL,
    compare_at_price NUMERIC(19, 2),
    warranty_months  INT           NOT NULL DEFAULT 12,
    specs            JSONB         NOT NULL DEFAULT '{}'::jsonb,
    keywords         TEXT          NOT NULL DEFAULT '',
    featured         BOOLEAN       NOT NULL DEFAULT FALSE,
    status           VARCHAR(20)   NOT NULL DEFAULT 'ACTIVE',
    created_at       TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ   NOT NULL DEFAULT now(),
    version          BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT product_slug_format CHECK (slug ~ '^[a-z0-9]+(-[a-z0-9]+)*$'),
    CONSTRAINT product_price_positive CHECK (price > 0),
    CONSTRAINT product_compare_at_above_price CHECK (compare_at_price IS NULL OR compare_at_price > price),
    CONSTRAINT product_warranty_range CHECK (warranty_months BETWEEN 0 AND 240),
    CONSTRAINT product_status CHECK (status IN ('ACTIVE', 'DRAFT', 'DISCONTINUED')),
    CONSTRAINT product_specs_object CHECK (jsonb_typeof(specs) = 'object')
);

-- Weighted full-text document: name and SKU matter most, then brand/keywords, then summary.
-- Brand and category names are denormalised into `search_text` by the application because generated
-- columns cannot reference other tables.
ALTER TABLE product ADD COLUMN search_text TEXT NOT NULL DEFAULT '';
ALTER TABLE product ADD COLUMN search_vector TSVECTOR GENERATED ALWAYS AS (
    setweight(to_tsvector('english', coalesce(name, '') || ' ' || coalesce(sku, '')), 'A') ||
    setweight(to_tsvector('english', coalesce(search_text, '') || ' ' || coalesce(keywords, '')), 'B') ||
    setweight(to_tsvector('english', coalesce(summary, '')), 'C')
) STORED;

CREATE INDEX product_category_price_idx ON product (category_id, price);
CREATE INDEX product_brand_idx ON product (brand_id);
CREATE INDEX product_status_idx ON product (status);
CREATE INDEX product_specs_gin ON product USING GIN (specs jsonb_path_ops);
CREATE INDEX product_search_gin ON product USING GIN (search_vector);
CREATE INDEX product_name_trgm ON product USING GIN (name gin_trgm_ops);

CREATE TABLE product_image (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    product_id BIGINT       NOT NULL REFERENCES product (id) ON DELETE CASCADE,
    url_large  VARCHAR(300) NOT NULL,
    url_small  VARCHAR(300) NOT NULL,
    width      INT          NOT NULL,
    height     INT          NOT NULL,
    alt        VARCHAR(300) NOT NULL,
    -- EXACT: this model. PRODUCT_LINE: same line/older generation. REPRESENTATIVE: same kind of product.
    match_type VARCHAR(20)  NOT NULL,
    credit     VARCHAR(300) NOT NULL,
    source_url VARCHAR(500) NOT NULL,
    sort_order INT          NOT NULL DEFAULT 0,
    CONSTRAINT product_image_match CHECK (match_type IN ('EXACT', 'PRODUCT_LINE', 'REPRESENTATIVE')),
    CONSTRAINT product_image_dims CHECK (width > 0 AND height > 0)
);
CREATE INDEX product_image_product_idx ON product_image (product_id, sort_order);

-- Stock status (IN_STOCK, LOW_STOCK, OUT_OF_STOCK, BACKORDER, DISCONTINUED) is derived from these numbers
-- and the product status, never stored, so it can't drift.
CREATE TABLE inventory (
    product_id          BIGINT      PRIMARY KEY REFERENCES product (id) ON DELETE CASCADE,
    available           INT         NOT NULL,
    reserved            INT         NOT NULL DEFAULT 0,
    low_stock_threshold INT         NOT NULL DEFAULT 5,
    backorder_allowed   BOOLEAN     NOT NULL DEFAULT FALSE,
    restock_target      INT         NOT NULL DEFAULT 0,
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    version             BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT inventory_available_non_negative CHECK (available >= 0),
    CONSTRAINT inventory_reserved_non_negative CHECK (reserved >= 0),
    CONSTRAINT inventory_threshold_non_negative CHECK (low_stock_threshold >= 0)
);

-- Curated, editorial product groupings used by home-page rows ("homelab-starter", "developer-setup"...).
CREATE TABLE product_collection (
    product_id BIGINT      NOT NULL REFERENCES product (id) ON DELETE CASCADE,
    tag        VARCHAR(60) NOT NULL,
    sort_order INT         NOT NULL DEFAULT 0,
    PRIMARY KEY (tag, product_id),
    CONSTRAINT product_collection_tag_format CHECK (tag ~ '^[a-z0-9]+(-[a-z0-9]+)*$')
);
