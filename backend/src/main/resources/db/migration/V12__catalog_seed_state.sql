-- Fingerprint of the catalog seed files last loaded successfully. A restart with unchanged files skips seeding,
-- which otherwise re-reads every product on each boot (minutes on a small instance).
CREATE TABLE catalog_seed_state (
    id          SMALLINT PRIMARY KEY DEFAULT 1 CHECK (id = 1),
    fingerprint TEXT        NOT NULL,
    products    INTEGER     NOT NULL,
    seeded_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);
