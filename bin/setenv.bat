@echo off
rem quotes are required for correct handling of path with spaces

rem default java exe for running the wrapper
rem note this is not the java exe for running the application. the exe for running the application is defined in the wrapper configuration file
if not "%JAVA_HOME%"=="" (
    set java_exe="%JAVA_HOME%\bin\java.exe"
    set javaw_exe="%JAVA_HOME%\bin\javaw.exe"
) else (
    echo JAVA_HOME is not defined. Please refer the documentation on how to setup JAVA_HOME.
    exit /b -1
)

rem default java home
set wrapper_home=%~dp0..\wrapper

rem location of the wrapper jar file. necessary lib files will be loaded by this jar. they must be at <wrapper_home>/lib/...
set wrapper_jar="%wrapper_home%\wrapper.jar"
set wrapper_app_jar="%wrapper_home%\wrapperApp.jar"

rem setting java options for wrapper process. depending on the scripts used, the wrapper may require more memory.
set wrapper_java_options=-Xmx30m -Djna.tmpdir="%wrapper_home%\tmp"

rem wrapper bat file for running the wrapper
set wrapper_bat="%~dp0wrapper.bat"

rem configuration file used by all bat files
set conf_file="%wrapper_home%\conf\wrapper.conf"

rem default configuration used in genConfig
set conf_default_file="%wrapper_home%\conf\wrapper.conf.default"
