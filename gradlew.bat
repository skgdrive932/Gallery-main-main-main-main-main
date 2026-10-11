@echo off
setlocal

set GRADLE_VERSION=8.10.2
set DIST_NAME=gradle-%GRADLE_VERSION%-bin.zip
set CACHE_DIR=%USERPROFILE%\.gradle\wrapper\dists\gradle-%GRADLE_VERSION%
set ZIP_PATH=%CACHE_DIR%\%DIST_NAME%
set INSTALL_DIR=%CACHE_DIR%\gradle-%GRADLE_VERSION%

if exist "%INSTALL_DIR%\bin\gradle.bat" (
  call "%INSTALL_DIR%\bin\gradle.bat" %*
  exit /b %ERRORLEVEL%
)

if not exist "%CACHE_DIR%" mkdir "%CACHE_DIR%"

if not exist "%ZIP_PATH%" (
  echo Downloading Gradle %GRADLE_VERSION%...
  powershell -NoProfile -ExecutionPolicy Bypass -Command "Invoke-WebRequest -UseBasicParsing -Uri 'https://services.gradle.org/distributions/%DIST_NAME%' -OutFile '%ZIP_PATH%'"
)

if exist "%INSTALL_DIR%" rmdir /s /q "%INSTALL_DIR%"
powershell -NoProfile -ExecutionPolicy Bypass -Command "Expand-Archive -Force '%ZIP_PATH%' '%CACHE_DIR%'"

call "%INSTALL_DIR%\bin\gradle.bat" %*
exit /b %ERRORLEVEL%
