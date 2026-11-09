-- V2: personal API tokens (used by bots: "Authorization: Bearer <token>").
-- Only a SHA-256 hash is stored; the token is shown to the user once when created.
CREATE TABLE api_tokens (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id       BIGINT       NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    token_hash    VARCHAR(64)  NOT NULL UNIQUE,
    name          VARCHAR(60)  NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    last_used_at  TIMESTAMPTZ
);
CREATE INDEX api_tokens_user_idx ON api_tokens (user_id);
