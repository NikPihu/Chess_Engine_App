@echo off
rem Runs a chess engine (default: chess_engine_v1). Examples:
rem   engine "position startpos" d
rem   engine gui
rem   engine                        (interactive: type commands, 'help' lists them)
rem Use another engine: set ENGINE_JAR=chess_engine_v2\target\chess_engine_v2.jar
rem Build first with: mvn -q package -DskipTests

rem (--add-modules turns on SIMD for the NNUE engine; 2>nul hides Java's 'incubator module' notice)
if "%ENGINE_JAR%"=="" set "ENGINE_JAR=chess_engine_v1\target\chess_engine_v1.jar"
java --add-modules jdk.incubator.vector -jar "%~dp0%ENGINE_JAR%" %* 2>nul
