@echo off
rem chess_engine_v3 (C++) shortcut.
rem   tools\engine3 build           configure (first time) and build Release with MSVC
rem   tools\engine3 test            build, then run all tests
rem   tools\engine3 bench           fixed workload: node count + speed
rem   tools\engine3 "perft 5" d     any UCI command lines, then exit
rem   tools\engine3                 interactive UCI
rem The engine binary is chess_engine_v3\bin\engine_v3.exe (what fastchess / lichess-bot run).
setlocal
pushd "%~dp0.."
set V3=chess_engine_v3
if /I "%~1"=="build" goto build
if /I "%~1"=="test" goto test
if not exist %V3%\bin\engine_v3.exe call :build || goto end
%V3%\bin\engine_v3.exe %*
goto end

:build
if not exist %V3%\build\CMakeCache.txt cmake -S %V3% -B %V3%\build -G "Visual Studio 18 2026" -A x64 || exit /b 1
cmake --build %V3%\build --config Release || exit /b 1
exit /b 0

:test
call :build || goto end
%V3%\bin\v3_tests.exe %2

:end
popd
endlocal
