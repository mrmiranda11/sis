@echo off
cd /d %wrapper_home%\conf

set WRAPPER_CONF=wrapper.conf
set SEARCH=\
set REPLACE=/

(for /f "delims=" %%i in ('type "%WRAPPER_CONF%" ^& break ^> "%WRAPPER_CONF%" ') do (
    if not "%%i"=="" (
        set "line=%%i"
        setlocal ENABLEDELAYEDEXPANSION
        >> "%WRAPPER_CONF%" echo !line:%SEARCH%=%REPLACE%!
        endlocal
    )
))