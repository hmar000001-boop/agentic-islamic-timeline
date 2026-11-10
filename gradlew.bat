@echo off
setlocal
set VERSION=9.5.0
set EXPECTED=553c78f50dafcd54d65b9a444649057857469edf836431389695608536d6b746
set CACHE=%USERPROFILE%\.gradle\agentic-wrapper\gradle-%VERSION%
set BIN=%CACHE%\gradle-%VERSION%\bin\gradle.bat
if not exist "%BIN%" (
  if not exist "%CACHE%" mkdir "%CACHE%"
  powershell -NoProfile -ExecutionPolicy Bypass -Command "$ErrorActionPreference='Stop'; $v='%VERSION%'; $c='%CACHE%'; $u='https://services.gradle.org/distributions/gradle-'+$v+'-bin.zip'; $z=Join-Path $c 'gradle.zip'; Invoke-WebRequest $u -OutFile $z; $actual=(Get-FileHash $z -Algorithm SHA256).Hash.ToLower(); if($actual -ne '%EXPECTED%'){Remove-Item $z -Force; throw 'Gradle distribution SHA-256 mismatch'}; Expand-Archive -Force $z $c; Remove-Item $z -Force"
  if errorlevel 1 exit /b 1
)
call "%BIN%" -p "%~dp0" %*
exit /b %ERRORLEVEL%
