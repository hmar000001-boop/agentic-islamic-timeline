@echo off
setlocal EnableExtensions
set "BASE_DIR=%~dp0"
set "MAVEN_PATH_FILE=%TEMP%\retail-agent-maven-%RANDOM%-%RANDOM%.txt"

powershell.exe -NoLogo -NoProfile -NonInteractive -ExecutionPolicy Bypass -File "%BASE_DIR%scripts\bootstrap-maven.ps1" -ProjectRoot "%BASE_DIR%" -ResultFile "%MAVEN_PATH_FILE%"
if errorlevel 1 (
  if exist "%MAVEN_PATH_FILE%" del /q "%MAVEN_PATH_FILE%" >nul 2>&1
  exit /b 1
)

set /p MAVEN_VERSION=<"%MAVEN_PATH_FILE%"
for /f "usebackq skip=1 delims=" %%H in ("%MAVEN_PATH_FILE%") do set "MAVEN_SHA512=%%H"
del /q "%MAVEN_PATH_FILE%" >nul 2>&1
if not defined MAVEN_VERSION (
  echo Maven bootstrap did not return a distribution version. 1>&2
  exit /b 1
)
if not defined MAVEN_SHA512 (
  echo Maven bootstrap did not return a distribution checksum. 1>&2
  exit /b 1
)
if not defined MAVEN_USER_HOME set "MAVEN_USER_HOME=%USERPROFILE%\.m2"
set "MAVEN_EXECUTABLE=%MAVEN_USER_HOME%\wrapper\dists\apache-maven-%MAVEN_VERSION%\%MAVEN_SHA512%\bin\mvn.cmd"
if not exist "%MAVEN_EXECUTABLE%" (
  echo Pinned Maven executable was not found after bootstrap. 1>&2
  exit /b 1
)

"%MAVEN_EXECUTABLE%" -f "%BASE_DIR%pom.xml" %*
exit /b %ERRORLEVEL%
