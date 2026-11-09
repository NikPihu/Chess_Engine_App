-- V5: game reviews (engine analysis of a whole game, computed by a background worker).
--
-- The table doubles as a durable job queue: the worker claims QUEUED rows with
-- "FOR UPDATE SKIP LOCKED", so a restart never loses a request and two workers never take the same
-- one.
CREATE TABLE game_reviews (
    game_id       VARCHAR(12)  PRIMARY KEY REFERENCES games (id) ON DELETE CASCADE,
    requested_by  BIGINT       NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    status        VARCHAR(10)  NOT NULL,              -- QUEUED | RUNNING | DONE | FAILED
    result        JSONB,                              -- see ReviewService.Review
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    finished_at   TIMESTAMPTZ
);
CREATE INDEX game_reviews_queue_idx ON game_reviews (created_at) WHERE status = 'QUEUED';
CREATE INDEX game_reviews_user_day_idx ON game_reviews (requested_by, created_at);
