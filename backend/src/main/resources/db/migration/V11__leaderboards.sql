-- Virtual-spending leaderboards.
--
-- Rankings are computed from virtual_purchase (the source of truth): nothing here stores spend totals, so there is
-- nothing to reset at a month boundary and nothing that can drift from order history. This table only holds each
-- account's public identity and privacy choices.
CREATE TABLE leaderboard_profile (
    user_id      BIGINT      PRIMARY KEY REFERENCES app_user (id) ON DELETE CASCADE,
    -- Public alias; never derived from the email. Generated (e.g. Collector48213) until the user picks one.
    display_name CITEXT      NOT NULL UNIQUE,
    -- Privacy by default: hidden accounts are ranked but shown as "Anonymous collector".
    visible      BOOLEAN     NOT NULL DEFAULT FALSE,
    -- Show the account's profile photo (e.g. from Google) only if the user opts in.
    show_avatar  BOOLEAN     NOT NULL DEFAULT FALSE,
    -- Operators can exclude an account (e.g. testing) without touching its orders.
    eligible     BOOLEAN     NOT NULL DEFAULT TRUE,
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT leaderboard_display_name_format CHECK (display_name ~ '^[A-Za-z0-9_]{3,20}$')
);

-- Ranking scans completed orders, optionally within a month. A partial index keeps that scan small and ignores
-- cancelled and returned orders entirely.
CREATE INDEX virtual_purchase_leaderboard_idx ON virtual_purchase (created_at, shopper_id, total)
    WHERE status = 'COMPLETED';

-- Rank milestone notifications ("You entered the monthly Top 50") have their own switch.
ALTER TABLE notification_preference ADD COLUMN leaderboard_updates BOOLEAN NOT NULL DEFAULT TRUE;
