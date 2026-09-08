@echo off
chcp 65001 >nul
cd /d "%~dp0"
set LMPC_FROM_BAT=1

rem "call" is required: python may resolve to a .bat shim (pyenv). Without
rem "call" control never returns here and the window closes instantly.
rem Keep this file pure ASCII: cyrillic after "chcp" corrupts batch parsing.
where python >nul 2>nul
if not errorlevel 1 goto usepython
where py >nul 2>nul
if not errorlevel 1 goto usepy

echo.
echo Python not found / Python ne naiden.
echo.
echo Install: https://www.python.org/downloads/
echo During install tick "Add Python to PATH".
goto end

:usepython
call python "%~dp0tools\run.py"
goto end

:usepy
call py "%~dp0tools\run.py"

:end
echo.
pause
