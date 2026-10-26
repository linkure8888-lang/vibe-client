@echo off
echo Installing Vibe Client for Windows...
echo Copying files to .minecraft/mods and setting up launcher profiles...
if not exist "%appdata%\.minecraft\mods" mkdir "%appdata%\.minecraft\mods"
copy /y "..\..\build\libs\*.jar" "%appdata%\.minecraft\mods\"
echo Vibe Client installed successfully!
pause
