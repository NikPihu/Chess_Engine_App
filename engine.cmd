@echo off
rem Runs the engine. Examples:
rem   engine "position startpos" d
rem   engine                        (interactive: type commands, 'help' lists them)
rem Build first with: mvn -q package -DskipTests
java -jar "%~dp0target\engine.jar" %*
