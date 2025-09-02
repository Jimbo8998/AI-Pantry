@echo off
setlocal
set "MAVEN_VERSION=3.9.9"
set "BASE_DIR=%~dp0"
set "WRAPPER_DIR=%BASE_DIR%.mvn"
set "MAVEN_HOME=%WRAPPER_DIR%\apache-maven-%MAVEN_VERSION%"
set "MVN_CMD=%MAVEN_HOME%\bin\mvn.cmd"

if exist "%MVN_CMD%" goto run

echo.
echo Downloading Apache Maven %MAVEN_VERSION% (one-time)...
set "ZIP=%TEMP%\apache-maven-%MAVEN_VERSION%-bin.zip"
set "URL1=https://downloads.apache.org/maven/maven-3/%MAVEN_VERSION%/binaries/apache-maven-%MAVEN_VERSION%-bin.zip"
set "URL2=https://dlcdn.apache.org/maven/maven-3/%MAVEN_VERSION%/binaries/apache-maven-%MAVEN_VERSION%-bin.zip"
set "URL3=https://archive.apache.org/dist/maven/maven-3/%MAVEN_VERSION%/binaries/apache-maven-%MAVEN_VERSION%-bin.zip"

del /f /q "%ZIP%" >NUL 2>&1
for %%U in ("%URL1%" "%URL2%" "%URL3%") do (
  echo Trying %%~U
  powershell -NoProfile -ExecutionPolicy Bypass -Command ^
    "try { Invoke-WebRequest -Uri ('%%~U') -OutFile ('%ZIP%'); } catch { }"
  if exist "%ZIP%" goto unzip
)
echo Failed to download Maven from all mirrors. Check your internet connection and try again.
exit /b 1

:unzip
mkdir "%WRAPPER_DIR%" >NUL 2>&1
powershell -NoProfile -ExecutionPolicy Bypass -Command ^
  "Expand-Archive -Path ('%ZIP%') -DestinationPath ('%WRAPPER_DIR%') -Force"

:run
"%MVN_CMD%" %*
endlocal
