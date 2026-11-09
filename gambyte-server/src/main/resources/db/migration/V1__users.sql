-- V1: accounts, ratings and one-time email tokens.
--
-- Flyway applies migrations in version order exactly once. Never edit a migration that has run
-- anywhere; add a new V<n>__*.sql instead.

CREATE TABLE users (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    username        VARCHAR(30)  NOT NULL,
    email           VARCHAR(254),                 -- null for system accounts (the engine bot)
    password_hash   VARCHAR(255),                 -- null for system accounts / OAuth-only users
    role            VARCHAR(16)  NOT NULL DEFAULT 'USER',   -- USER | MOD | ADMIN
    title           VARCHAR(8),                   -- BOT for bot accounts, null for humans
    country         VARCHAR(2),
    bio             VARCHAR(400),
    email_verified  BOOLEAN      NOT NULL DEFAULT FALSE,
    banned          BOOLEAN      NOT NULL DEFAULT FALSE,
    muted           BOOLEAN      NOT NULL DEFAULT FALSE,
    premium_until   TIMESTAMPTZ,                  -- premium while now() < premium_until
    settings        JSONB        NOT NULL DEFAULT '{}'::jsonb,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    last_seen_at    TIMESTAMPTZ
);
-- Usernames and emails are unique ignoring case ("Magnus" and "magnus" are the same account).
CREATE UNIQUE INDEX users_username_lower_uq ON users (lower(username));
CREATE UNIQUE INDEX users_email_lower_uq ON users (lower(email)) WHERE email IS NOT NULL;

-- One Glicko-2 rating per user and "perf" (bullet, blitz, rapid, classical, puzzle).
CREATE TABLE ratings (
    user_id     BIGINT           NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    perf        VARCHAR(16)      NOT NULL,
    rating      DOUBLE PRECISION NOT NULL DEFAULT 1500,
    deviation   DOUBLE PRECISION NOT NULL DEFAULT 350,
    volatility  DOUBLE PRECISION NOT NULL DEFAULT 0.06,
    games       INT              NOT NULL DEFAULT 0,
    best        INT              NOT NULL DEFAULT 1500,
    updated_at  TIMESTAMPTZ      NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, perf)
);
CREATE INDEX ratings_leaderboard_idx ON ratings (perf, rating DESC);

-- Email verification / password reset links. Only a SHA-256 hash of the token is stored, so a
-- database leak does not leak usable links.
CREATE TABLE email_tokens (
    token_hash  VARCHAR(64)  PRIMARY KEY,
    user_id     BIGINT       NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    purpose     VARCHAR(16)  NOT NULL,            -- VERIFY | RESET
    expires_at  TIMESTAMPTZ  NOT NULL
);

-- The built-in engine plays as this bot account (games against it need a real user row).
INSERT INTO users (username, role, title, bio, email_verified)
VALUES ('GambyteEngine', 'USER', 'BOT',
        'Gambyte''s own engine (C++, NNUE, self-trained). Levels 1-8; level 8 is full strength.', TRUE);
