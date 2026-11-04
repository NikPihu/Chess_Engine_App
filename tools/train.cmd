@echo off
rem Trains the next generation of chess_engine_v2's network (one per run), then stops.
rem   tools\train                 train the next generation; promoted automatically if SPRT proves it better
rem   tools\train --status        list every generation and which one is live
rem   tools\train --rollback 2    make gen-2 live again (the Lichess bot uses it from its next game)
rem   tools\train --stop          stop a running generation from another window
rem   tools\train --generations 3 several in a row
rem Ctrl+C in this window also stops it cleanly. Build first: mvn -q package -DskipTests
setlocal
pushd "%~dp0.."
python chess_engine_v2\train\loop.py %*
popd
endlocal
