-- V6: lesson progress, follows, notifications and player reports.

CREATE TABLE lesson_progress (
    user_id          BIGINT      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    slug             VARCHAR(60) NOT NULL,
    completed_steps  INT         NOT NULL,
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, slug)
);

-- "A follows B". Friends = people who follow each other.
CREATE TABLE follows (
    follower_id  BIGINT      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    followee_id  BIGINT      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (follower_id, followee_id)
);
CREATE INDEX follows_followee_idx ON follows (followee_id);

CREATE TABLE notifications (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id     BIGINT       NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    kind        VARCHAR(24)  NOT NULL,           -- follow | challenge | game | premium | report | system
    text        VARCHAR(300) NOT NULL,
    link        VARCHAR(200),
    read        BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX notifications_user_idx ON notifications (user_id, created_at DESC);

CREATE TABLE reports (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    reporter_id  BIGINT       NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    reported_id  BIGINT       NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    reason       VARCHAR(16)  NOT NULL,          -- cheat | abuse | other
    text         VARCHAR(1000),
    game_id      VARCHAR(12),
    status       VARCHAR(10)  NOT NULL DEFAULT 'OPEN',   -- OPEN | CLOSED
    resolution   VARCHAR(300),
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX reports_open_idx ON reports (created_at) WHERE status = 'OPEN';
