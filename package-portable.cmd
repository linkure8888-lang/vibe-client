@echo off
rem ===========================================================================
rem  Vibe Client - build a PORTABLE build (no installer, no admin rights)
rem
rem  Why this exists: the .exe installer is a WiX bootstrapper that unpacks
rem  itself into %TEMP% at run time, and on some machines that step fails with
rem  "createTempFile(, .tmp, ...) failed" before the app is ever touched. A
rem  portable build has no bootstrapper, so that failure mode cannot happen.
rem
rem  Output:
rem    launcher\out\portable\VibeClient\VibeClient.exe   <- run this
rem    VibeClient-portable.zip                           <- what to hand to people
rem
rem  It carries its own trimmed Java runtime, so the target machine needs no
rem  Java installed.
rem ===========================================================================
setlocal EnableDelayedExpansion

set "PROJECT_DIR=%~dp0"
set "PROJECT_DIR=%PROJECT_DIR:~0,-1%"
cd /d "%PROJECT_DIR%" || goto :fail

set "APP_NAME=VibeClient"
set "LAUNCHER_DIR=%PROJECT_DIR%\launcher"
set "OUT=%LAUNCHER_DIR%\out"
set "INPUT=%OUT%\input-portable"
set "PORTABLE=%OUT%\portable"
set "ZIP=%PROJECT_DIR%\VibeClient-portable.zip"

echo.
echo ==========================================================
echo   Vibe Client - build portable package
echo ==========================================================
echo.

rem --- 0. Locate JDK -----------------------------------------------------
set "JAVA_HOME_USE=C:\Program Files\Java\jdk-23"
if not exist "%JAVA_HOME_USE%\bin\jpackage.exe" (
    for /d %%D in ("C:\Program Files\Java\jdk-*") do (
        if exist "%%D\bin\jpackage.exe" set "JAVA_HOME_USE=%%D"
    )
)
if not exist "%JAVA_HOME_USE%\bin\jpackage.exe" (
    echo ERROR: jpackage not found. Install JDK 17+.
    goto :fail
)
set "PATH=%JAVA_HOME_USE%\bin;%PATH%"
echo [1/5] JDK : %JAVA_HOME_USE%

rem --- 1. Locate the bundled Gson jar ------------------------------------
rem Same two traps as the installer build: only search `files-2.1` (a full
rem scan matches Gradle's metadata stubs, which have no classes), and use
rem `dir /s /b` because `for /r` reports phantom paths in hash-named trees.
set "GSON_JAR="
set "GSON_DIR=%PROJECT_DIR%\gradle-home\caches\modules-2\files-2.1\com.google.code.gson"
if exist "%GSON_DIR%" (
    for /f "delims=" %%f in ('dir /s /b "%GSON_DIR%\gson-2.10.1.jar" 2^>nul') do (
        if not defined GSON_JAR set "GSON_JAR=%%f"
    )
)
if not defined GSON_JAR (
    echo ERROR: gson-2.10.1.jar not found. Run a Gradle build once first.
    goto :fail
)
echo       Gson: %GSON_JAR%

rem --- 2. Compile the launcher -------------------------------------------
rem Sources go into an @argfile with FORWARD slashes: javac treats a backslash
rem in an argfile as an escape, so "C:\Users" silently becomes "C:Users".
echo [2/5] Compiling launcher...
set "CLASSES=%LAUNCHER_DIR%\classes"
if exist "%CLASSES%" rmdir /s /q "%CLASSES%"
mkdir "%CLASSES%"
set "ARGFILE=%OUT%\sources-portable.txt"
if not exist "%OUT%" mkdir "%OUT%"
if exist "%ARGFILE%" del "%ARGFILE%"
for /r "%LAUNCHER_DIR%\src" %%f in (*.java) do (
    set "SRC=%%f"
    setlocal EnableDelayedExpansion
    echo "!SRC:\=/!">>"%ARGFILE%"
    endlocal
)
javac -encoding UTF-8 -cp "%GSON_JAR%" -d "%CLASSES%" "@%ARGFILE%"
if errorlevel 1 goto :fail
del "%ARGFILE%" >nul 2>nul

rem --- 3. Collect the payload --------------------------------------------
echo [3/5] Collecting app files...
if exist "%INPUT%" rmdir /s /q "%INPUT%"
mkdir "%INPUT%"

rem Class-Path in the manifest, not a jpackage flag: jpackage has no
rem --class-path and rejects it outright.
set "MANIFEST=%OUT%\manifest-portable.txt"
> "%MANIFEST%" echo Main-Class: com.vibe.launcher.LauncherMain
>>"%MANIFEST%" echo Class-Path: gson.jar
>>"%MANIFEST%" echo.
jar --create --file "%INPUT%\launcher.jar" --manifest "%MANIFEST%" -C "%CLASSES%" . -C "%LAUNCHER_DIR%\assets" vibeclient-256.png
if errorlevel 1 goto :fail
del "%MANIFEST%" >nul 2>nul

copy /Y "%GSON_JAR%" "%INPUT%\gson.jar" >nul
if errorlevel 1 goto :fail

rem The mod jar rides along so the launcher can install it into the game.
set "MOD_JAR="
for %%f in ("%PROJECT_DIR%\build\libs\*.jar") do (
    echo %%~nf | findstr /i /c:"-sources" >nul || set "MOD_JAR=%%f"
)
if not "%MOD_JAR%"=="" (
    copy /Y "%MOD_JAR%" "%INPUT%\" >nul
    echo       mod: %MOD_JAR%
) else (
    echo       WARNING: no mod jar found - run the Gradle build first.
)

rem --- 4. Build the app image --------------------------------------------
rem --type app-image produces a plain folder with its own runtime and an
rem .exe entry point. No MSI, no bootstrapper, nothing to unpack to %TEMP%.
echo [4/5] Building portable app image...
if exist "%PORTABLE%" rmdir /s /q "%PORTABLE%"
mkdir "%PORTABLE%"

jpackage ^
  --type app-image ^
  --name "%APP_NAME%" ^
  --app-version 1.0 ^
  --input "%INPUT%" ^
  --main-jar "launcher.jar" ^
  --main-class com.vibe.launcher.LauncherMain ^
  --dest "%PORTABLE%" ^
  --icon "%LAUNCHER_DIR%\assets\vibeclient.ico" ^
  --description "Vibe Client launcher" ^
  --vendor "Vibe" ^
  --java-options "-Xmx256m"
if errorlevel 1 goto :fail

rem --- 5. Zip it ----------------------------------------------------------
echo [5/5] Zipping...
if exist "%ZIP%" del "%ZIP%"
powershell -NoProfile -Command "Compress-Archive -Path '%PORTABLE%\%APP_NAME%' -DestinationPath '%ZIP%' -CompressionLevel Optimal -Force"
if errorlevel 1 (
    echo       WARNING: zip failed - the folder is still usable.
)

echo.
echo ==========================================================
echo   PORTABLE BUILD OK
echo ==========================================================
echo   Folder : %PORTABLE%\%APP_NAME%\VibeClient.exe
if exist "%ZIP%" for %%f in ("%ZIP%") do echo   Zip    : %%f (%%~zf bytes)
echo.
echo   No installation and no admin rights needed. Unzip anywhere
echo   and run VibeClient.exe.
echo.
exit /b 0

:fail
echo.
echo ==========================================================
echo   PORTABLE BUILD FAILED - see the messages above
echo ==========================================================
exit /b 1
