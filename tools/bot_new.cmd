@echo off
rem Runs chess_engine_v3 (C++) on Lichess through lichess-bot. NOT used until you switch:
rem the live bot is still tools\bot (v2). Switching = stop tools\bot, then start tools\bot_new.
rem   tools\bot_new          play with v3: 4 threads, 256 MB hash, Syzygy tables, online tablebase
rem Settings: tools\lichess-bot\config_new.yml (a copy of config.yml with the v3 engine settings).
rem Build first: tools\engine3 build
setlocal
pushd "%~dp0.."
if not exist chess_engine_v3\bin\engine_v3.exe (
  echo chess_engine_v3\bin\engine_v3.exe not found - run: tools\engine3 build
  popd
  exit /b 1
)
if not exist tools\lichess-bot\config_new.yml (
  echo tools\lichess-bot\config_new.yml not found
  popd
  exit /b 1
)
echo Playing with chess_engine_v3\bin\engine_v3.exe
copy /y chess_engine_v3\bin\engine_v3.exe tools\lichess-bot\engines\engine_v3.exe >nul
pushd tools\lichess-bot
venv\Scripts\python.exe lichess-bot.py --config config_new.yml %*
popd
popd
endlocal
