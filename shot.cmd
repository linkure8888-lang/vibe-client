@echo off
setlocal EnableDelayedExpansion
set "ROOT=%~dp0"
set "ROOT=%ROOT:~0,-1%"
cd /d "%ROOT%"
set "JDK=C:\Program Files\Java\jdk-23"
set "GSON="
for /f "delims=" %%f in ('dir /s /b "%ROOT%\gradle-home\caches\modules-2\files-2.1\com.google.code.gson\gson-2.10.1.jar" 2^>nul') do (
    if not defined GSON set "GSON=%%f"
)
if not defined GSON exit /b 1
if exist "%ROOT%\launcher\classes" rmdir /s /q "%ROOT%\launcher\classes"
mkdir "%ROOT%\launcher\classes"
set "ARGFILE=%ROOT%\launcher\sources.txt"
if exist "%ARGFILE%" del "%ARGFILE%"
for /r "%ROOT%\launcher\src" %%f in (*.java) do (
    set "SRC=%%f"
    setlocal EnableDelayedExpansion
    echo "!SRC:\=/!">>"%ARGFILE%"
    endlocal
)
"%JDK%\bin\javac.exe" -encoding UTF-8 -cp "%GSON%" -d "%ROOT%\launcher\classes" "@%ARGFILE%"
if errorlevel 1 exit /b 1
del "%ARGFILE%"
if exist "%ROOT%\launcher\shot\classes" rmdir /s /q "%ROOT%\launcher\shot\classes"
mkdir "%ROOT%\launcher\shot\classes"
"%JDK%\bin\javac.exe" -encoding UTF-8 -cp "%ROOT%\launcher\classes;%GSON%" -d "%ROOT%\launcher\shot\classes" "%ROOT%\launcher\shot\Shot.java"
if errorlevel 1 exit /b 1
"%JDK%\bin\java.exe" -cp "%ROOT%\launcher\classes;%ROOT%\launcher\assets;%ROOT%\launcher\shot\classes;%GSON%" shot.Shot "%ROOT%\launcher\shot\launcher.png" 1180 760
exit /b 0
