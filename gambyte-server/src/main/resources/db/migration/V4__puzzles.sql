-- V4: puzzles, who solved what, and Puzzle Rush scores.
CREATE TABLE puzzles (
    id           VARCHAR(16)      PRIMARY KEY,
    fen          VARCHAR(100)     NOT NULL,      -- position the solver sees (their turn)
    setup_move   VARCHAR(5),                     -- opponent's last move, animated first (optional)
    solution     VARCHAR(100)     NOT NULL,      -- UCI moves: solver, reply, solver, ...
    rating       DOUBLE PRECISION NOT NULL,
    deviation    DOUBLE PRECISION NOT NULL DEFAULT 200,
    volatility   DOUBLE PRECISION NOT NULL DEFAULT 0.06,
    plays        INT              NOT NULL DEFAULT 0,
    themes       VARCHAR(200)     NOT NULL DEFAULT '',
    source       VARCHAR(16)      NOT NULL,      -- mined (our engine) | lichess (CC0 import)
    created_at   TIMESTAMPTZ      NOT NULL DEFAULT now()
);
-- "A puzzle near rating R": range scan on this index.
CREATE INDEX puzzles_rating_idx ON puzzles (rating);

CREATE TABLE puzzle_attempts (
    user_id     BIGINT      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    puzzle_id   VARCHAR(16) NOT NULL REFERENCES puzzles (id) ON DELETE CASCADE,
    solved      BOOLEAN     NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, puzzle_id)
);
CREATE INDEX puzzle_attempts_day_idx ON puzzle_attempts (user_id, created_at);

CREATE TABLE puzzle_rush_runs (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id     BIGINT      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    score       INT         NOT NULL,
    mode        VARCHAR(8)  NOT NULL,           -- 3min | survival
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX puzzle_rush_best_idx ON puzzle_rush_runs (mode, score DESC);
