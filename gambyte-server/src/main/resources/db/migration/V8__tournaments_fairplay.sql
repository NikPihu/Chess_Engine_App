-- V8: arena tournaments and fair-play checks.

CREATE TABLE tournaments (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name        VARCHAR(80)  NOT NULL,
    initial     INT          NOT NULL,           -- seconds
    increment   INT          NOT NULL,
    rated       BOOLEAN      NOT NULL DEFAULT TRUE,
    starts_at   TIMESTAMPTZ  NOT NULL,
    minutes     INT          NOT NULL,           -- duration of the arena
    status      VARCHAR(10)  NOT NULL DEFAULT 'CREATED',   -- CREATED | STARTED | FINISHED
    created_by  BIGINT       REFERENCES users (id) ON DELETE SET NULL,
    winner_id   BIGINT       REFERENCES users (id) ON DELETE SET NULL,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX tournaments_status_idx ON tournaments (status, starts_at);

CREATE TABLE tournament_players (
    tournament_id  BIGINT   NOT NULL REFERENCES tournaments (id) ON DELETE CASCADE,
    user_id        BIGINT   NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    score          INT      NOT NULL DEFAULT 0,
    games          INT      NOT NULL DEFAULT 0,
    wins_in_row    INT      NOT NULL DEFAULT 0,  -- 2+ = "on fire": wins count double
    rating         INT      NOT NULL DEFAULT 1500,
    withdrawn      BOOLEAN  NOT NULL DEFAULT FALSE,
    last_opponent  BIGINT,
    PRIMARY KEY (tournament_id, user_id)
);

-- Focus-loss counts reported by the browser during the game (fair-play signal).
ALTER TABLE games ADD COLUMN white_blurs INT NOT NULL DEFAULT 0;
ALTER TABLE games ADD COLUMN black_blurs INT NOT NULL DEFAULT 0;

-- One row per (game, player) to check, doubling as the job queue.
CREATE TABLE fairplay_checks (
    game_id          VARCHAR(12)      NOT NULL REFERENCES games (id) ON DELETE CASCADE,
    user_id          BIGINT           NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    status           VARCHAR(10)      NOT NULL DEFAULT 'QUEUED',  -- QUEUED | DONE | FAILED
    moves_checked    INT,
    acpl             DOUBLE PRECISION,              -- average centipawn loss
    top_match        DOUBLE PRECISION,              -- share of moves equal to the engine's choice
    time_cv          DOUBLE PRECISION,              -- move-time variation (low = robotic)
    blurs            INT,
    rating           INT,
    suspicion        DOUBLE PRECISION,              -- 0..1
    flagged          BOOLEAN          NOT NULL DEFAULT FALSE,
    reviewed         BOOLEAN          NOT NULL DEFAULT FALSE,
    created_at       TIMESTAMPTZ      NOT NULL DEFAULT now(),
    PRIMARY KEY (game_id, user_id)
);
CREATE INDEX fairplay_queue_idx ON fairplay_checks (created_at) WHERE status = 'QUEUED';
CREATE INDEX fairplay_flagged_idx ON fairplay_checks (created_at DESC) WHERE flagged AND NOT reviewed;
