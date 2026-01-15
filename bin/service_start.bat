@echo off
cd %~dp0
call setenv.bat
if %errorlevel%==-1 exit /b %errorlevel%

%wrapper_bat% -t %conf_file%
pause