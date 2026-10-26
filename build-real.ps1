if (Test-Path "dist_releases") { Remove-Item -Recurse -Force "dist_releases" }
New-Item -ItemType Directory -Path "dist_releases" | Out-Null
Write-Host "Packaging Linux distribution..."
$linuxDir = "dist_releases/VibeClient-Linux"
New-Item -ItemType Directory -Path "$linuxDir" | Out-Null
New-Item -ItemType Directory -Path "$linuxDir/launcher" | Out-Null
if (Test-Path "launcher/classes") { Copy-Item -Recurse -Force "launcher/classes" "$linuxDir/launcher/" }
if (Test-Path "launcher/assets") { Copy-Item -Recurse -Force "launcher/assets" "$linuxDir/launcher/" }
if (Test-Path "launcher/out/launcher.jar") { Copy-Item -Force "launcher/out/launcher.jar" "$linuxDir/launcher/" }
$linuxRunner = "#!/bin/bash`n# Vibe Client for Linux`nDIR=\"`$(cd \"`$(dirname \"`$({BASH_SOURCE[0]})`")`" && pwd)\"`nJAVA_EXEC=\"java\"`nif [ -n \"\$JAVA_HOME\" ]; then JAVA_EXEC=\"\$JAVA_HOME/bin/java\"; fi`nexec \"\$JAVA_EXEC\" -cp \"\$DIR/launcher/classes:\$DIR/launcher/assets:\$DIR/launcher/launcher.jar\" com.vibe.launcher.LauncherMain \"\$@\""
Set-Content -Path "$linuxDir/vibe-client.sh" -Value $linuxRunner -Encoding utf8
Compress-Archive -Path "$linuxDir/*" -DestinationPath "VibeClient-Linux.zip" -Force
Write-Host "Packaging macOS distribution (.app bundle)..."
$macApp = "dist_releases/VibeClient.app/Contents"
New-Item -ItemType Directory -Path "$macApp/MacOS" | Out-Null
New-Item -ItemType Directory -Path "$macApp/Resources/launcher" | Out-Null
if (Test-Path "launcher/classes") { Copy-Item -Recurse -Force "launcher/classes" "$macApp/Resources/launcher/" }
if (Test-Path "launcher/assets") { Copy-Item -Recurse -Force "launcher/assets" "$macApp/Resources/launcher/" }
if (Test-Path "launcher/out/launcher.jar") { Copy-Item -Force "launcher/out/launcher.jar" "$macApp/Resources/launcher/" }
$macRunner = "#!/bin/bash`n# Vibe Client for macOS`nDIR=\"`$(cd \"`$(dirname \"`$({BASH_SOURCE[0]})`")`" && pwd)\"`nexec java -cp \"\$DIR/../Resources/launcher/classes:\$DIR/../Resources/launcher/assets:\$DIR/../Resources/launcher/launcher.jar\" com.vibe.launcher.LauncherMain \"\$@\""
Set-Content -Path "$macApp/MacOS/vibe-client" -Value $macRunner -Encoding utf8
$plist = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>`n<!DOCTYPE plist PUBLIC \"-//Apple//DTD PLIST 1.0//EN\" \"http://www.apple.com/DTDs/PropertyList-1.0.dtd\">`n<plist version=\"1.0\">`n<dict>`n<key>CFBundleName</key><string>VibeClient</string>`n<key>CFBundleExecutable</key><string>vibe-client</string>`n<key>CFBundleIdentifier</key><string>com.vibe.client</string>`n</dict>`n</plist>"
Set-Content -Path "$macApp/Info.plist" -Value $plist -Encoding utf8
Compress-Archive -Path "dist_releases/VibeClient.app" -DestinationPath "VibeClient-macOS.zip" -Force
Remove-Item -Recurse -Force "dist_releases"
Write-Host "Successfully generated VibeClient-Linux.zip and VibeClient-macOS.zip!"