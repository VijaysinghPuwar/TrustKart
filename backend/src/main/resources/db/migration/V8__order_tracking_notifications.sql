-- Order tracking: a purchase can now be cancelled before it ships (CANCELLED) or returned after delivery
-- (REFUNDED). Both release stock and return funds; refunded_at records when either happened. The delivery
-- stage itself is not stored: it is computed from created_at and the clock, so it can never drift.
ALTER TABLE virtual_purchase DROP CONSTRAINT virtual_purchase_status;
ALTER TABLE virtual_purchase ADD CONSTRAINT virtual_purchase_status
    CHECK (status IN ('COMPLETED', 'CANCELLED', 'REFUNDED'));
ALTER TABLE virtual_purchase DROP CONSTRAINT virtual_purchase_refund_state;
ALTER TABLE virtual_purchase ADD CONSTRAINT virtual_purchase_refund_state
    CHECK ((status IN ('CANCELLED', 'REFUNDED')) = (refunded_at IS NOT NULL));

-- In-app notifications. Generated idempotently from order milestones: dedupe_key identifies the event
-- (e.g. "order:<uuid>:SHIPPED"), so regenerating never creates duplicates.
CREATE TABLE notification (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    public_id   UUID         NOT NULL UNIQUE,
    shopper_id  BIGINT       NOT NULL REFERENCES shopper (id) ON DELETE CASCADE,
    type        VARCHAR(32)  NOT NULL,
    dedupe_key  VARCHAR(120) NOT NULL,
    title       VARCHAR(120) NOT NULL,
    body        VARCHAR(300) NOT NULL,
    link        VARCHAR(200),
    image_url   VARCHAR(300),
    created_at  TIMESTAMPTZ  NOT NULL,
    read_at     TIMESTAMPTZ,
    CONSTRAINT notification_dedupe UNIQUE (shopper_id, dedupe_key),
    CONSTRAINT notification_link_local CHECK (link IS NULL OR link LIKE '/%')
);
CREATE INDEX notification_shopper_idx ON notification (shopper_id, created_at DESC);
CREATE INDEX notification_unread_idx ON notification (shopper_id) WHERE read_at IS NULL;

CREATE TABLE notification_preference (
    shopper_id       BIGINT      PRIMARY KEY REFERENCES shopper (id) ON DELETE CASCADE,
    order_updates    BOOLEAN     NOT NULL DEFAULT TRUE,
    delivery_updates BOOLEAN     NOT NULL DEFAULT TRUE,
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);
