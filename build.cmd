@echo off
rem ===========================================================================
rem  Vibe Client - Windows build script
rem  Double-click this file (or run it from cmd) to build the mod.
rem
rem  It downloads Gradle INTO the project folder (tools\) the first time,
rem  then builds and copies the finished jar into the game's mods\ folder.
rem
rem  Nothing is installed globally. Delete tools\ and gradle-home\ to reclaim
rem  the disk space (~400 MB).
rem ===========================================================================
setlocal EnableDelayedExpansion

set "PROJECT_DIR=%~dp0"
set "PROJECT_DIR=%PROJECT_DIR:~0,-1%"
cd /d "%PROJECT_DIR%" || goto :fail

rem --- Everything Gradle downloads stays inside the project ------------------
set "GRADLE_USER_HOME=%PROJECT_DIR%\gradle-home"

set "GRADLE_VER=8.9"
set "GRADLE_DIR=%PROJECT_DIR%\tools\gradle-%GRADLE_VER%"
set "GRADLE_BIN=%GRADLE_DIR%\bin\gradle.bat"

echo.
echo ==========================================================
echo   Vibe Client - build
echo ==========================================================
echo   project : %PROJECT_DIR%
echo   gradle  : %GRADLE_VER%
echo.

rem --- 1. Fetch Gradle into the project if it is missing --------------------
if not exist "%GRADLE_BIN%" (
    echo [1/3] Downloading Gradle %GRADLE_VER% into tools\ ...
    if not exist "%PROJECT_DIR%\tools" mkdir "%PROJECT_DIR%\tools"
    set "ZIP=%PROJECT_DIR%\tools\gradle-%GRADLE_VER%-bin.zip"
    powershell -NoProfile -ExecutionPolicy Bypass -Command ^
      "$ErrorActionPreference='Stop'; Invoke-WebRequest -Uri 'https://services.gradle.org/distributions/gradle-%GRADLE_VER%-bin.zip' -OutFile '%ZIP%' -UseBasicParsing; Expand-Archive -Path '%ZIP%' -DestinationPath '%PROJECT_DIR%\tools' -Force; Remove-Item '%ZIP%'"
    if errorlevel 1 goto :fail
    echo       done.
) else (
    echo [1/3] Gradle already present.
)

rem --- 2. Build -------------------------------------------------------------
echo [2/3] Building (first run downloads Minecraft deps, be patient)...
call "%GRADLE_BIN%" build --no-daemon --console=plain
if errorlevel 1 goto :fail

rem --- 3. Copy the jar where the game can load it ---------------------------
set "JAR="
for %%f in ("%PROJECT_DIR%\build\libs\*.jar") do (
    echo %%~nf | findstr /i /c:"-sources" >nul || set "JAR=%%f"
)
if "%JAR%"=="" (
    echo [3/3] ERROR: no jar found in build\libs
    goto :fail
)

rem Try to find the real .minecraft folder; fall back to a local mods\ dir.
set "MODS_DIR=%APPDATA%\.minecraft\mods"
if not exist "%APPDATA%\.minecraft" set "MODS_DIR=%PROJECT_DIR%\mods"
if not exist "%MODS_DIR%" mkdir "%MODS_DIR%"

copy /Y "%JAR%" "%MODS_DIR%\" >nul
if errorlevel 1 goto :fail

echo [3/3] Copied to: %MODS_DIR%
echo.
echo ==========================================================
echo   BUILD OK
echo ==========================================================
echo   jar : %JAR%
echo   mods: %MODS_DIR%
echo.
echo   Next: install Fabric Loader for Minecraft and start the game.
echo.
pause
exit /b 0

:fail
echo.
echo ==========================================================
echo   BUILD FAILED - see the messages above
echo ==========================================================
pause
exit /b 1