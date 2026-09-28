-- Identity, RBAC, sessions and login events.

CREATE EXTENSION IF NOT EXISTS citext;

CREATE TABLE app_user (
    id                   BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    public_id            UUID         NOT NULL UNIQUE,
    email                CITEXT       NOT NULL UNIQUE,
    email_verified       BOOLEAN      NOT NULL DEFAULT FALSE,
    display_name         VARCHAR(80)  NOT NULL,
    password_hash        VARCHAR(255) NOT NULL,
    status               VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    failed_login_count   INT          NOT NULL DEFAULT 0,
    locked_until         TIMESTAMPTZ,
    password_changed_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    last_login_at        TIMESTAMPTZ,
    created_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),
    version              BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT app_user_status CHECK (status IN ('ACTIVE', 'DISABLED')),
    CONSTRAINT app_user_failed_non_negative CHECK (failed_login_count >= 0)
);

CREATE TABLE role (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name        VARCHAR(40)  NOT NULL UNIQUE,
    description VARCHAR(200) NOT NULL
);

CREATE TABLE permission (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name        VARCHAR(60)  NOT NULL UNIQUE,
    description VARCHAR(200) NOT NULL
);

CREATE TABLE role_permission (
    role_id       BIGINT NOT NULL REFERENCES role (id) ON DELETE CASCADE,
    permission_id BIGINT NOT NULL REFERENCES permission (id) ON DELETE CASCADE,
    PRIMARY KEY (role_id, permission_id)
);

CREATE TABLE user_role (
    user_id BIGINT NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    role_id BIGINT NOT NULL REFERENCES role (id),
    PRIMARY KEY (user_id, role_id)
);

-- One row per signed-in device. The refresh token itself is never stored, only its SHA-256.
-- previous_token_hash lets us detect replay of an already-rotated refresh token (token theft).
CREATE TABLE user_session (
    id                  UUID         PRIMARY KEY,
    user_id             BIGINT       NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    token_hash          VARCHAR(64)     NOT NULL UNIQUE,
    previous_token_hash VARCHAR(64),
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    last_used_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    expires_at          TIMESTAMPTZ  NOT NULL,
    revoked_at          TIMESTAMPTZ,
    revoke_reason       VARCHAR(40),
    ip_address          VARCHAR(45),
    user_agent          VARCHAR(300),
    device_label        VARCHAR(120)
);
CREATE INDEX user_session_user_idx ON user_session (user_id, revoked_at);
CREATE INDEX user_session_previous_hash_idx ON user_session (previous_token_hash);

-- Authentication attempts. email_hash (SHA-256 of the lowercased address) supports per-account analysis
-- without storing addresses typed by strangers for accounts that don't exist.
CREATE TABLE login_event (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id    BIGINT       REFERENCES app_user (id) ON DELETE CASCADE,
    email_hash VARCHAR(64)     NOT NULL,
    outcome    VARCHAR(30)  NOT NULL,
    ip_address VARCHAR(45),
    user_agent VARCHAR(300),
    session_id UUID,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT login_event_outcome CHECK (outcome IN
        ('SUCCESS', 'BAD_CREDENTIALS', 'LOCKED', 'DISABLED', 'RATE_LIMITED', 'REFRESH_REUSE_DETECTED', 'LOGOUT'))
);
CREATE INDEX login_event_user_idx ON login_event (user_id, created_at DESC);
CREATE INDEX login_event_created_idx ON login_event (created_at DESC);

-- Every visitor is a shopper. Guests get one on first write (cart, wallet...), identified by an opaque
-- HttpOnly cookie; signing in attaches or merges it into the user's shopper. Carts, wallets, purchases and
-- collections are owned by shoppers, so guest mode needs no special cases downstream.
CREATE TABLE shopper (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    public_id    UUID        NOT NULL UNIQUE,
    guest_token_hash VARCHAR(64) UNIQUE,
    user_id      BIGINT      UNIQUE REFERENCES app_user (id) ON DELETE CASCADE,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_seen_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT shopper_guest_or_user CHECK (user_id IS NOT NULL OR guest_token_hash IS NOT NULL)
);
CREATE INDEX shopper_guest_cleanup_idx ON shopper (last_seen_at) WHERE user_id IS NULL;

-- Roles and permissions. Permissions are what the code checks; roles are bundles an admin assigns.
INSERT INTO permission (name, description) VALUES
    ('catalog:write', 'Create and edit products, categories and prices'),
    ('inventory:write', 'Adjust stock levels'),
    ('purchase:read:any', 'View any shopper''s virtual purchases'),
    ('purchase:refund:any', 'Refund any virtual purchase'),
    ('user:read', 'View customer accounts'),
    ('user:manage', 'Change roles and disable accounts'),
    ('security:read', 'View login events, sessions and security alerts'),
    ('session:revoke:any', 'Sign any user out of a session'),
    ('audit:read', 'View the audit log'),
    ('analytics:read', 'View virtual sales analytics'),
    ('system:read', 'View system health details');

INSERT INTO role (name, description) VALUES
    ('CUSTOMER', 'Shops, manages own account'),
    ('SUPPORT', 'Helps customers: reads accounts and purchases, can refund'),
    ('CATALOG_MANAGER', 'Manages products and stock; no access to account security data'),
    ('ORDER_MANAGER', 'Manages virtual purchases and refunds'),
    ('SECURITY_ANALYST', 'Investigates security events; cannot change products or prices'),
    ('ADMIN', 'Full administrative access');

INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id FROM role r JOIN permission p ON
    (r.name = 'SUPPORT' AND p.name IN ('user:read', 'purchase:read:any', 'purchase:refund:any'))
 OR (r.name = 'CATALOG_MANAGER' AND p.name IN ('catalog:write', 'inventory:write'))
 OR (r.name = 'ORDER_MANAGER' AND p.name IN ('purchase:read:any', 'purchase:refund:any', 'analytics:read'))
 OR (r.name = 'SECURITY_ANALYST' AND p.name IN ('security:read', 'session:revoke:any', 'audit:read', 'user:read'))
 OR (r.name = 'ADMIN');
