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

set "DERBY_LOG=%CLIENT_SOLUTION%\logs\derby.log"
call "%CONNECTIVITY_HOME%\bin\setOpts.bat"

java %BOOTSTRAP_DEBUG_OPT% %SECURITY_POLICY_OPT% -classpath .;"%CONNECTIVITY_HOME%"\lib\core\enterprise-bootstrap.jar;"%CONNECTIVITY_HOME%"\bin;"%CLIENT_SOLUTION%"\conf\system;"%CLIENT_SOLUTION%"\conf\da -Dclient.solution.home="%CLIENT_SOLUTION%" -Dib.home="%CONNECTIVITY_HOME%" -Dlogback.configurationFile="%CLIENT_SOLUTION%"/conf/system/logback.xml -Dgroovy.source.encoding=UTF-8 -Dderby.stream.error.file="%DERBY_LOG%" com.experian.eda.enterprise.startup.Bootstrap -fa "%CLIENT_SOLUTION%"/conf/system/camel-context.xml
pause
