@echo off
setlocal ENABLEDELAYEDEXPANSION
rem Package required items into AI-Pantry-Canvas.zip for Canvas submission

set "DEST=AI-Pantry-Canvas.zip"
set "STAGE=canvas_pkg"

echo [1/4] Staging files...
rd /s /q "%STAGE%" 2>nul
mkdir "%STAGE%" || (
  echo Failed to create staging folder & exit /b 1
)

rem Required items
if not exist src (
  echo ERROR: src folder not found & exit /b 1
)
robocopy src "%STAGE%\src" /E >nul

if not exist pom.xml (
  echo ERROR: pom.xml not found & exit /b 1
)
copy /y pom.xml "%STAGE%\" >nul

if not exist mvnw.cmd (
  echo ERROR: mvnw.cmd not found & exit /b 1
)
copy /y mvnw.cmd "%STAGE%\" >nul

if exist .mvn (
  echo Including Maven wrapper directory (.mvn)
  robocopy .mvn "%STAGE%\.mvn" /E >nul
) else (
  echo WARNING: .mvn directory not found; mvnw may not run without it.
)

if exist doc (
  robocopy doc "%STAGE%\doc" /E >nul
) else (
  echo WARNING: doc folder not found; run "mvnw.cmd javadoc:javadoc" first.
)

if exist README.md (
  copy /y README.md "%STAGE%\" >nul
) else (
  echo WARNING: README.md not found
)

if exist screenshots (
  echo Including optional screenshots folder
  robocopy screenshots "%STAGE%\screenshots" /E >nul
)

echo [2/4] Removing previous archive (if any)...
del /f /q "%DEST%" 2>nul

echo [3/4] Creating ZIP archive "%DEST%"...

rem Prefer PowerShell CreateFromDirectory (handles long paths better)
powershell -NoProfile -ExecutionPolicy Bypass -Command "try {Add-Type -A 'System.IO.Compression.FileSystem'; [IO.Compression.ZipFile]::CreateFromDirectory('%STAGE%','%DEST%'); exit 0} catch { exit 1 }"
if errorlevel 1 (
  rem Fallback to Windows tar (bsdtar)
  where tar >nul 2>&1 && (
    tar -a -c -f "%DEST%" -C "%STAGE%" .
  )
)

if not exist "%DEST%" (
  rem Last resort: use jar from JDK
  where jar >nul 2>&1 && (
    jar -cMf "%DEST%" -C "%STAGE%" .
  )
)

if not exist "%DEST%" (
  echo ERROR: Failed to create %DEST%
  exit /b 1
)

for %%F in ("%DEST%") do set "SIZE=%%~zF"
echo [4/4] Done: %DEST% (%SIZE% bytes)
endlocal
exit /b 0
