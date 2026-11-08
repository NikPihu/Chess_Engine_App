@echo off
rem Trains the next generation of chess_engine_v3's network (one per run), then stops.
rem   tools\train_new                    one generation; promoted to chess_engine_v3\nets\best.nnue
rem                                      only after the blunder gate + SPRT H1 (anchor match recorded)
rem   tools\train_new --status           every generation and which one is live
rem   tools\train_new --rollback 2       make v3 gen-2 live again
rem   tools\train_new --stop             stop a running generation from another window
rem   tools\train_new --generations 3    several in a row
rem Promotion is automatic: the v3 bot (tools\bot_new) plays chess_engine_v3\nets\best.nnue from its next game.
rem Build first: tools\engine3 build
setlocal
pushd "%~dp0.."
python chess_engine_v3\train_new\loop.py %*
popd
endlocal
