-- V3: games.
--
-- A row is written when a game starts (status STARTED) and completed when it ends. While a game is
-- running its moves and clocks live in server memory and in Redis (fast); Postgres gets the
-- finished game once. See game/README.md.
CREATE TABLE games (
    id                 VARCHAR(12)  PRIMARY KEY,           -- short random id, e.g. q7ZvsdUF
    white_id           BIGINT       NOT NULL REFERENCES users (id),
    black_id           BIGINT       NOT NULL REFERENCES users (id),
    white_rating       INT,
    black_rating       INT,
    white_rating_diff  INT,
    black_rating_diff  INT,
    engine_level       INT,                                -- set when one side is Gambyte Engine
    perf               VARCHAR(16)  NOT NULL,              -- bullet | blitz | rapid | classical
    clock_initial      INT          NOT NULL,              -- seconds; 0 = untimed
    clock_increment    INT          NOT NULL,              -- seconds
    rated              BOOLEAN      NOT NULL,
    source             VARCHAR(16)  NOT NULL,              -- lobby | computer | friend | bot | arena
    status             VARCHAR(16)  NOT NULL,              -- STARTED, MATE, RESIGN, TIMEOUT, ...
    winner             VARCHAR(5),                          -- white | black | null (draw/aborted)
    termination        VARCHAR(80),                         -- human text: "White wins by checkmate"
    start_fen          VARCHAR(100) NOT NULL,
    moves              TEXT         NOT NULL DEFAULT '',   -- UCI moves separated by spaces
    move_times         TEXT         NOT NULL DEFAULT '',   -- think time per move in ms, comma-separated
    final_fen          VARCHAR(100),
    ply                INT          NOT NULL DEFAULT 0,
    tournament_id      BIGINT,
    opening            VARCHAR(80),
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    ended_at           TIMESTAMPTZ
);
-- A player's game archive, newest first (keyset pagination on created_at).
CREATE INDEX games_white_idx ON games (white_id, created_at DESC);
CREATE INDEX games_black_idx ON games (black_id, created_at DESC);
-- Games that were running when the server stopped (restored at start-up).
CREATE INDEX games_started_idx ON games (status) WHERE status = 'STARTED';
CREATE INDEX games_tournament_idx ON games (tournament_id) WHERE tournament_id IS NOT NULL;
