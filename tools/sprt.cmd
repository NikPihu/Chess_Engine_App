@echo off
rem SPRT match between two engine versions. Examples:
rem   tools\sprt v1 random
rem   tools\sprt v2 v1 --tc 5+0.05 --label "phase 6 search"
cd /d "%~dp0.."
java tools\Sprt.java %*
