@echo off
rem ===========================================================================
rem  Vibe Client - build a real Windows .exe installer
rem
rem  Uses jpackage from the JDK + WiX (both already on this machine).
rem  WiX is installed system-wide at "C:\Program Files (x86)\WiX Toolset v3.14";
rem  we only point PATH at it for this process - nothing is installed here.
rem
rem  Output:
rem    launcher\out\installer\VibeClient.exe   <- installs the app
rem
rem  The installer creates a Desktop shortcut and a Start Menu entry, and
rem  bundles its own trimmed Java runtime, so end users need NO Java.
rem
rem  Run this AFTER build.cmd has produced build\libs\vibeclient-*.jar.
rem ===========================================================================
setlocal EnableDelayedExpansion

set "PROJECT_DIR=%~dp0"
set "PROJECT_DIR=%PROJECT_DIR:~0,-1%"
cd /d "%PROJECT_DIR%" || goto :fail

set "APP_NAME=VibeClient"
set "LAUNCHER_DIR=%PROJECT_DIR%\launcher"
set "CLASSES=%LAUNCHER_DIR%\classes"
set "OUT=%LAUNCHER_DIR%\out"
set "INPUT=%OUT%\input"

echo.
echo ==========================================================
echo   Vibe Client - package Windows installer (jpackage)
echo ==========================================================
echo.

rem --- 0. Locate JDK tools + WiX ----------------------------------------
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

set "WIX_DIR="
for /d %%D in ("C:\Program Files (x86)\WiX Toolset*") do set "WIX_DIR=%%D"
if not defined WIX_DIR (
    echo ERROR: WiX Toolset not found in Program Files ^(x86^).
    echo        Install it from https://wixtoolset.org/
    goto :fail
)
set "PATH=%WIX_DIR%\bin;%JAVA_HOME_USE%\bin;%PATH%"
echo [1/5] JDK : %JAVA_HOME_USE%
echo       WiX : %WIX_DIR%

rem --- 1. Locate the bundled Gson jar -----------------------------------
rem Two traps handled here:
rem  - a recursive scan of the whole module cache also matches Gradle's
rem    `metadata-*` descriptor stubs, which carry the same file name but no
rem    classes, producing ~170 "package com.google.gson does not exist" errors;
rem  - `for /r` reports paths that do not exist when the directory contains
rem    deep hash-named subfolders (it returned a phantom path with the hash
rem    segment dropped), so `dir /s /b` is used: it lists real files only.
set "GSON_JAR="
set "GSON_DIR=%PROJECT_DIR%\gradle-home\caches\modules-2\files-2.1\com.google.code.gson"
if exist "%GSON_DIR%" (
    for /f "delims=" %%f in ('dir /s /b "%GSON_DIR%\gson-2.10.1.jar" 2^>nul') do (
        if not defined GSON_JAR set "GSON_JAR=%%f"
    )
)
if not defined GSON_JAR (
    echo ERROR: gson-2.10.1.jar not found in gradle-home. Run build.cmd once first.
    goto :fail
)
if not exist "%GSON_JAR%" (
    echo ERROR: resolved Gson path does not exist: %GSON_JAR%
    goto :fail
)
echo       Gson: %GSON_JAR%

rem --- 2. Compile the launcher -------------------------------------------
rem Source paths go into an @argfile instead of a command line. Building a
rem quoted list with `call set` produced literal \" sequences that javac
rem rejected outright, and an argfile sidesteps Windows quoting entirely.
rem
rem Paths are written with FORWARD slashes: javac reads @argfiles as a
rem Java-properties-like format where a backslash is an escape character, so
rem "C:\Users" silently becomes "C:Users". Forward slashes are valid in javac
rem paths on Windows and need no escaping.
echo [2/5] Compiling launcher...
if exist "%CLASSES%" rmdir /s /q "%CLASSES%"
mkdir "%CLASSES%"
set "ARGFILE=%OUT%\sources.txt"
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

rem --- 3. Main jar for the launcher -------------------------------------
rem The icon PNG is packed INTO launcher.jar: the window icon is loaded as a
rem classpath resource, so it must travel with the jar rather than sit next to
rem it (jpackage would put it in the app dir, off the classpath).
rem
rem Gson is wired in through the jar MANIFEST's Class-Path, not through a
rem jpackage option: jpackage has no --class-path (it rejects the flag), and a
rem manifest entry is the standard way to attach a side-by-side jar. The path
rem is relative, so it resolves inside the installed app image.
echo [3/5] Packaging launcher jar...
if not exist "%OUT%" mkdir "%OUT%"
set "MANIFEST=%OUT%\manifest.txt"
> "%MANIFEST%" echo Main-Class: com.vibe.launcher.LauncherMain
>>"%MANIFEST%" echo Class-Path: gson.jar
>>"%MANIFEST%" echo.
rem Home screen artwork: the world cards and the compressed hero scenes.
rem The full-res art/ PNGs (2-4.6 MB each) stay OUT of the jar - the
rem compressed copies are what HomeScreen loads (see ImageStore).
jar --create --file "%OUT%\launcher.jar" --manifest "%MANIFEST%" -C "%CLASSES%" . -C "%LAUNCHER_DIR%\assets" vibeclient-256.png -C "%LAUNCHER_DIR%\assets" cards -C "%LAUNCHER_DIR%\assets" compressed
if errorlevel 1 goto :fail
del "%MANIFEST%" >nul 2>nul

rem Gson is a runtime dependency: copy it next to the launcher jar.
copy /Y "%GSON_JAR%" "%OUT%\gson.jar" >nul
if errorlevel 1 goto :fail

rem --- 3. Collect app resources into the input folder -------------------
if exist "%INPUT%" rmdir /s /q "%INPUT%"
mkdir "%INPUT%"

rem The launcher.jar MUST live inside the input folder.
copy /Y "%OUT%\launcher.jar" "%INPUT%\launcher.jar" >nul
if errorlevel 1 goto :fail
copy /Y "%OUT%\gson.jar" "%INPUT%\gson.jar" >nul
if errorlevel 1 goto :fail

set "MOD_JAR="
for %%f in ("%PROJECT_DIR%\build\libs\*.jar") do (
    echo %%~nf | findstr /i /c:"-sources" >nul || set "MOD_JAR=%%f"
)
if not "%MOD_JAR%"=="" (
    copy /Y "%MOD_JAR%" "%INPUT%\" >nul
    echo       bundled mod: %MOD_JAR%
) else (
    echo       WARNING: no mod jar yet - run build.cmd first.
)

rem Ship the build scripts so the installed app can rebuild itself.
copy /Y "%PROJECT_DIR%\build.cmd" "%INPUT%\" >nul 2>nul
copy /Y "%PROJECT_DIR%\build.sh"  "%INPUT%\" >nul 2>nul

rem --- 4. Generate the installer ----------------------------------------
echo [4/5] Running jpackage (bundles a trimmed Java runtime)...
if exist "%OUT%\installer" rmdir /s /q "%OUT%\installer"
mkdir "%OUT%\installer"

rem The .ico must exist and be a real Windows icon, or jpackage aborts with
rem "Failed to update icon". It is produced by launcher\tools\IconGen and then
rem converted by tools\make-icon.ps1; see launcher\assets\README.md.
if not exist "%LAUNCHER_DIR%\assets\vibeclient.ico" (
    echo ERROR: launcher\assets\vibeclient.ico is missing.
    goto :fail
)

jpackage ^
  --type exe ^
  --name "%APP_NAME%" ^
  --app-version 1.0 ^
  --input "%INPUT%" ^
  --main-jar "launcher.jar" ^
  --main-class com.vibe.launcher.LauncherMain ^
  --dest "%OUT%\installer" ^
  --icon "%LAUNCHER_DIR%\assets\vibeclient.ico" ^
  --win-shortcut ^
  --win-menu ^
  --win-menu-group "Vibe Client" ^
  --win-dir-chooser ^
  --description "Vibe Client launcher" ^
  --vendor "Vibe" ^
  --java-options "-Xmx256m"
if errorlevel 1 goto :fail

rem --- 5. Report --------------------------------------------------------
echo [5/5] Done.
echo.
echo ==========================================================
echo   PACKAGE OK
echo ==========================================================
for %%f in ("%OUT%\installer\*.exe") do echo   %%f
echo.
echo   Double-click the .exe to install. It adds a Desktop and
echo   Start Menu shortcut named "%APP_NAME%".
echo.
exit /b 0

:fail
echo.
echo ==========================================================
echo   PACKAGING FAILED - see the messages above
echo ==========================================================
exit /b 1