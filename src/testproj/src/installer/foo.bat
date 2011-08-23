@echo off
if "%OS%" == "Windows_NT" @setlocal
rem ---------------------------------------------------------------------------
rem Dummy front end BAT script

set SQLSHELL_SCALA_OPTS=

rem Make sure Java user.home property accurately reflects home directory
if NOT "%HOME%"=="" set SQLSHELL_SCALA_OPTS=%SQLSHELL_SCALA_OPTS% -Duser.home="%HOME%"

set _TOOL_CLASSPATH=
if "%_TOOL_CLASSPATH%"=="" (
  for %%f in ("$INSTALL_PATH\lib\*") do call :add_cpath "%%f"
  if "%OS%"=="Windows_NT" (
    for /d %%f in ("$INSTALL_PATH\lib\*") do call :add_cpath "%%f"
  )
)

if NOT "%CLASSPATH%" == "" call :add_cpath "%CLASSPATH%"

if "%SCALA_HOME%" == "" (
    @echo "SCALA_HOME is not set"
    goto end
)

java -cp "%_TOOL_CLASSPATH%" %SQLSHELL_SCALA_OPTS% org.clapper.bar.foo.Foo %1 %2 %3 %4 %5 %6 %7 %8 %9
goto end

rem ##########################################################################
rem # subroutines

:add_cpath
  if "%_TOOL_CLASSPATH%"=="" (
    set _TOOL_CLASSPATH=%~1
  ) else (
    set _TOOL_CLASSPATH=%_TOOL_CLASSPATH%;%~1
  )
goto :eof

:end
if "%OS%"=="Windows_NT" @endlocal


