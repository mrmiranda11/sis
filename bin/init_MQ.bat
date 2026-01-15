@echo off
TITLE=Connectivity
if "%OS%" == "Windows_NT" setlocal

set "CONNECTIVITY_HOME=C:\SIS_2_13"
if not "%CONNECTIVITY_HOME%" == "" goto okHome
set "CURRENT_DIR=%cd%"
set "CONNECTIVITY_HOME=%CURRENT_DIR%"
if exist "%CONNECTIVITY_HOME%\bin\run_connectivity.bat" goto okHome
cd ..
set "CONNECTIVITY_HOME=%cd%"
if exist "%CONNECTIVITY_HOME%\bin\run_connectivity.bat" goto okHome

:okHome

set "CLIENT_SOLUTION=%CONNECTIVITY_HOME%\sis"
if not "%CLIENT_SOLUTION%" == "" goto okCSHome
set "CLIENT_SOLUTION=%CONNECTIVITY_HOME%"

:okCSHome

if exist %CLIENT_SOLUTION%\lookup\incoming\Dummy.MQ.camelLock del %CLIENT_SOLUTION%\lookup\incoming\Dummy.MQ.camelLock
echo "" > %CLIENT_SOLUTION%\lookup\incoming\Dummy.MQ