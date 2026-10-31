@echo off
rem Runs our engine on Lichess through lichess-bot (tools\lichess-bot).
rem   tools\bot              check the setup / play (needs a BOT account)
rem   tools\bot -u           ONE-TIME, IRREVERSIBLE: upgrade the account to a BOT account
rem Build the engine first: mvn -q package -DskipTests
cd /d "%~dp0.."
if not exist target\engine.jar (
  echo target\engine.jar not found - run: mvn -q package -DskipTests
  exit /b 1
)
copy /y target\engine.jar tools\lichess-bot\engines\ChessEngineApp.jar >nul
cd tools\lichess-bot
venv\Scripts\python.exe lichess-bot.py %*
