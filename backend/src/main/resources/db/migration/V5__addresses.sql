-- Saved delivery addresses for the simulation. Nothing ships, so these may be fictional; the UI says so.
CREATE TABLE shopper_address (
    id          UUID         PRIMARY KEY,
    shopper_id  BIGINT       NOT NULL REFERENCES shopper (id) ON DELETE CASCADE,
    label       VARCHAR(40)  NOT NULL,
    full_name   VARCHAR(100) NOT NULL,
    line1       VARCHAR(120) NOT NULL,
    line2       VARCHAR(120),
    city        VARCHAR(80)  NOT NULL,
    region      VARCHAR(80),
    postal_code VARCHAR(16)  NOT NULL,
    country     CHAR(2)      NOT NULL,
    is_default  BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT shopper_address_country CHECK (country ~ '^[A-Z]{2}$')
);
CREATE INDEX shopper_address_shopper_idx ON shopper_address (shopper_id, created_at);
CREATE UNIQUE INDEX shopper_address_one_default ON shopper_address (shopper_id) WHERE is_default;
