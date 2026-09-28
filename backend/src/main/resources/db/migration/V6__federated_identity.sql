-- External sign-in identities (Sign in with Google). Accounts are matched on the provider's stable subject id,
-- never on email alone; an email match only links when the provider says the address is verified.
CREATE TABLE user_identity (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id    BIGINT       NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    provider   VARCHAR(20)  NOT NULL,
    subject    VARCHAR(255) NOT NULL,
    email      CITEXT       NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    last_used_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT user_identity_provider CHECK (provider IN ('google')),
    CONSTRAINT user_identity_unique UNIQUE (provider, subject),
    CONSTRAINT user_identity_one_per_user UNIQUE (user_id, provider)
);

ALTER TABLE login_event ADD COLUMN method VARCHAR(20) NOT NULL DEFAULT 'PASSWORD';
ALTER TABLE login_event ADD CONSTRAINT login_event_method CHECK (method IN ('PASSWORD', 'GOOGLE', 'REFRESH'));

-- Profile picture from the identity provider (a URL on the provider's CDN; TrustKart never proxies or stores the image).
ALTER TABLE app_user ADD COLUMN avatar_url VARCHAR(500);
ALTER TABLE app_user ADD CONSTRAINT app_user_avatar_https CHECK (avatar_url IS NULL OR avatar_url LIKE 'https://%');
