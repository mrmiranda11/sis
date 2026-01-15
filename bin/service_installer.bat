@echo off
cd /d %~dp0
call setenv.bat
if %errorlevel%==-1 exit /b %errorlevel%

call mod_wrapconf.bat

cd /d %~dp0
echo %java_exe% %wrapper_java_options% -jar %wrapper_jar% -i %conf_file%
%java_exe% %wrapper_java_options% -jar %wrapper_jar% -i %conf_file%