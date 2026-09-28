-- Cart, wishlists, virtual wallet ledger and virtual purchases.
-- Nothing here represents real money. Amounts are still NUMERIC(19,2)/BigDecimal and every balance change is
-- an append-only ledger entry, because correctness habits shouldn't depend on whether the money is real.

CREATE TABLE cart_item (
    id               UUID        PRIMARY KEY,
    shopper_id       BIGINT      NOT NULL REFERENCES shopper (id) ON DELETE CASCADE,
    product_id       BIGINT      NOT NULL REFERENCES product (id) ON DELETE CASCADE,
    quantity         INT         NOT NULL,
    saved_for_later  BOOLEAN     NOT NULL DEFAULT FALSE,
    added_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT cart_item_quantity_range CHECK (quantity BETWEEN 1 AND 99),
    CONSTRAINT cart_item_unique_product UNIQUE (shopper_id, product_id)
);

CREATE TABLE wishlist (
    id          UUID        PRIMARY KEY,
    shopper_id  BIGINT      NOT NULL REFERENCES shopper (id) ON DELETE CASCADE,
    name        VARCHAR(60) NOT NULL,
    is_default  BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT wishlist_unique_name UNIQUE (shopper_id, name)
);
-- At most one default list per shopper.
CREATE UNIQUE INDEX wishlist_one_default ON wishlist (shopper_id) WHERE is_default;

CREATE TABLE wishlist_item (
    wishlist_id UUID        NOT NULL REFERENCES wishlist (id) ON DELETE CASCADE,
    product_id  BIGINT      NOT NULL REFERENCES product (id) ON DELETE CASCADE,
    added_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (wishlist_id, product_id)
);

CREATE TABLE virtual_wallet (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    shopper_id  BIGINT         NOT NULL UNIQUE REFERENCES shopper (id) ON DELETE CASCADE,
    balance     NUMERIC(19, 2) NOT NULL,
    mode        VARCHAR(12)    NOT NULL DEFAULT 'BUDGET',
    created_at  TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ    NOT NULL DEFAULT now(),
    version     BIGINT         NOT NULL DEFAULT 0,
    CONSTRAINT virtual_wallet_balance_range CHECK (balance >= 0 AND balance <= 1000000000000.00),
    CONSTRAINT virtual_wallet_mode CHECK (mode IN ('BUDGET', 'UNLIMITED'))
);

-- Append-only ledger. The CHECK ties every row's amount to the balance movement it records.
CREATE TABLE virtual_transaction (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    public_id       UUID           NOT NULL UNIQUE,
    wallet_id       BIGINT         NOT NULL REFERENCES virtual_wallet (id) ON DELETE CASCADE,
    type            VARCHAR(12)    NOT NULL,
    amount          NUMERIC(19, 2) NOT NULL,
    balance_before  NUMERIC(19, 2) NOT NULL,
    balance_after   NUMERIC(19, 2) NOT NULL,
    reference       VARCHAR(40),
    description     VARCHAR(200)   NOT NULL,
    idempotency_key VARCHAR(64),
    created_at      TIMESTAMPTZ    NOT NULL DEFAULT now(),
    CONSTRAINT virtual_transaction_type CHECK (type IN ('CREDIT', 'PURCHASE', 'REFUND', 'RESET')),
    CONSTRAINT virtual_transaction_consistent CHECK (amount = balance_after - balance_before),
    CONSTRAINT virtual_transaction_non_negative CHECK (balance_before >= 0 AND balance_after >= 0),
    CONSTRAINT virtual_transaction_direction CHECK (
        (type = 'CREDIT' AND amount > 0) OR (type = 'PURCHASE' AND amount < 0) OR
        (type = 'REFUND' AND amount > 0) OR type = 'RESET'),
    CONSTRAINT virtual_transaction_idempotent UNIQUE (wallet_id, idempotency_key)
);
CREATE INDEX virtual_transaction_wallet_idx ON virtual_transaction (wallet_id, created_at DESC, id DESC);

CREATE SEQUENCE virtual_purchase_number_seq;

CREATE TABLE virtual_purchase (
    id                 BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    public_id          UUID           NOT NULL UNIQUE,
    order_number       VARCHAR(24)    NOT NULL UNIQUE,
    shopper_id         BIGINT         NOT NULL REFERENCES shopper (id) ON DELETE CASCADE,
    status             VARCHAR(12)    NOT NULL,
    item_count         INT            NOT NULL,
    subtotal           NUMERIC(19, 2) NOT NULL,
    shipping           NUMERIC(19, 2) NOT NULL DEFAULT 0,
    total              NUMERIC(19, 2) NOT NULL,
    wallet_mode        VARCHAR(12)    NOT NULL,
    balance_before     NUMERIC(19, 2),
    balance_after      NUMERIC(19, 2),
    delivery_preset    VARCHAR(20)    NOT NULL,
    simulation_address JSONB,
    idempotency_key    VARCHAR(64)    NOT NULL,
    request_hash       VARCHAR(64)    NOT NULL,
    created_at         TIMESTAMPTZ    NOT NULL DEFAULT now(),
    refunded_at        TIMESTAMPTZ,
    CONSTRAINT virtual_purchase_status CHECK (status IN ('COMPLETED', 'REFUNDED')),
    CONSTRAINT virtual_purchase_totals CHECK (total = subtotal + shipping AND total > 0 AND item_count > 0),
    CONSTRAINT virtual_purchase_idempotent UNIQUE (shopper_id, idempotency_key),
    CONSTRAINT virtual_purchase_refund_state CHECK ((status = 'REFUNDED') = (refunded_at IS NOT NULL))
);
CREATE INDEX virtual_purchase_shopper_idx ON virtual_purchase (shopper_id, created_at DESC);

-- Item snapshots: name and unit price as charged, independent of later catalog edits.
CREATE TABLE virtual_purchase_item (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    purchase_id  BIGINT         NOT NULL REFERENCES virtual_purchase (id) ON DELETE CASCADE,
    product_id   BIGINT         NOT NULL REFERENCES product (id),
    product_slug VARCHAR(120)   NOT NULL,
    product_name VARCHAR(200)   NOT NULL,
    category_name VARCHAR(120)  NOT NULL,
    image_url    VARCHAR(300),
    unit_price   NUMERIC(19, 2) NOT NULL,
    quantity     INT            NOT NULL,
    line_total   NUMERIC(19, 2) NOT NULL,
    -- Units actually taken from inventory (backorder lines take none); a refund returns exactly this many.
    stock_committed INT         NOT NULL DEFAULT 0,
    CONSTRAINT virtual_purchase_item_quantity CHECK (quantity > 0),
    CONSTRAINT virtual_purchase_item_stock CHECK (stock_committed BETWEEN 0 AND quantity),
    CONSTRAINT virtual_purchase_item_line CHECK (line_total = unit_price * quantity)
);
CREATE INDEX virtual_purchase_item_purchase_idx ON virtual_purchase_item (purchase_id);
CREATE INDEX virtual_purchase_item_product_idx ON virtual_purchase_item (product_id);
