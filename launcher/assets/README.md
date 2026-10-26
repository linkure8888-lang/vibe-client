# Vibe Client assets

## Icon

`vibeclient-256.png` — the launcher window icon. Packed into `launcher.jar` by
`package-exe.cmd` and loaded as a classpath resource.

`vibeclient.ico` — the Windows icon used by jpackage for the `.exe`, the
desktop shortcut and the Start Menu entry.

## Regenerating the icon

Two steps. The split exists for a concrete reason: jpackage refuses an `.ico`
that Java wrote by hand, but accepts one Windows itself produced.

```powershell
# 1. Draw the mark and write the PNG (and a best-effort ICO).
& "C:\Program Files\Java\jdk-23\bin\java.exe" launcher\tools\IconGen.java launcher\assets

# 2. Convert the PNG into a Windows-native ICO via System.Drawing.
powershell -File launcher\tools\make-icon.ps1
```

Step 2 is the one that matters. `IconGen` writes a correct ICO container, and
Windows Explorer renders it fine, but jpackage fails on it with
`Failed to update icon`. `System.Drawing.Icon.Save` writes the exact variant
jpackage expects, so the PNG is the source of truth and the ICO is always
derived from it.
