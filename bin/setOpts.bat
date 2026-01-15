@echo off

if not "%CLIENT_SOLUTION%" == "" goto gotIBHome
echo The CLIENT_SOLUTION variable is not set.
goto exit

:gotIBHome
REM Please comment/uncomment (i.e. add/remove the colon-colon notation) below to enable bootstrap debug and/or security policy for IB
::set BOOTSTRAP_DEBUG_OPT=-Djava.util.logging.config.file="%CLIENT_SOLUTION%"/conf/system/debug.properties
::set SECURITY_POLICY_OPT=-Djava.security.manager -Djava.security.policy="%CLIENT_SOLUTION%"/conf/system/ibsec.policy
goto end

:exit
exit /b 1

:end
exit /b 0