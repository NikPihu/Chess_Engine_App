# ADR-0003 — react-chessboard over chessground

- **Status:** accepted
- **Date:** 2026-10-31

## Context
The frontend needs a board component for React + TypeScript.

## Decision
Use **react-chessboard** (MIT). All rules and legality live on the server (our engine). The client may use
`chess.js` (BSD-2) only for instant UI feedback such as move highlighting.

## Consequences
- No licence obligations on our own code.
- It's slightly less polished than Lichess's board, but plenty for our needs.

## Alternatives considered
- **chessground (Lichess):** excellent, but GPL-3.0. Using it obliges us to publish the whole frontend under GPL.
- **chessboard.js:** jQuery-era, a poor fit for React.
