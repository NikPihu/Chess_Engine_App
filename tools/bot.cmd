@echo off
rem Runs a chess engine on Lichess through lichess-bot (tools\lichess-bot).
rem Works with any of our engines: they all speak UCI.
rem   tools\bot              play (needs a BOT account)
rem   tools\bot -u           ONE-TIME, IRREVERSIBLE: upgrade the account to a BOT account
rem Which engine plays: set ENGINE_JAR (default: chess_engine_v2 if built, else v1), e.g.
rem   set ENGINE_JAR=chess_engine_v1\target\chess_engine_v1.jar
rem With v2, every game loads chess_engine_v2\nets\best.nnue (EvalFile in config.yml), so a newly
rem promoted generation is used from the next game - no need to restart the bot.
rem Build first: mvn -q package -DskipTests
setlocal
pushd "%~dp0.."
if "%ENGINE_JAR%"=="" if exist chess_engine_v2\target\chess_engine_v2.jar set "ENGINE_JAR=chess_engine_v2\target\chess_engine_v2.jar"
if "%ENGINE_JAR%"=="" set "ENGINE_JAR=chess_engine_v1\target\chess_engine_v1.jar"
if not exist "%ENGINE_JAR%" (
  echo %ENGINE_JAR% not found - run: mvn -q package -DskipTests
  popd
  exit /b 1
)
echo Playing with %ENGINE_JAR%
copy /y "%ENGINE_JAR%" tools\lichess-bot\engines\ChessEngineApp.jar >nul
pushd tools\lichess-bot
venv\Scripts\python.exe lichess-bot.py %*
popd
popd
endlocal
