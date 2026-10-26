Add-Type -AssemblyName System.Drawing
$exe = "$env:ProgramFiles\VibeClient\VibeClient.exe"
$p = Start-Process -FilePath $exe -PassThru -WindowStyle Normal
Start-Sleep -Seconds 12
$procs = @(Get-Process -Name VibeClient -ErrorAction Stop)
$proc = $procs | Where-Object { $_.MainWindowHandle -ne 0 } | Select-Object -First 1
$h = [IntPtr]$proc.MainWindowHandle
$src = 'using System; using System.Runtime.InteropServices; public class Win32 { [DllImport("user32.dll")] public static extern bool GetWindowRect(IntPtr hWnd, out RECT rect); [DllImport("user32.dll")] public static extern bool SetForegroundWindow(IntPtr hWnd); [DllImport("user32.dll")] public static extern bool ShowWindow(IntPtr hWnd, int nCmdShow); public struct RECT { public int Left, Top, Right, Bottom; } }'
Add-Type -TypeDefinition $src
if ($h -ne [IntPtr]::Zero) {
[Win32]::ShowWindow($h, 9) | Out-Null
Start-Sleep -Milliseconds 400
[Win32]::SetForegroundWindow($h) | Out-Null
Start-Sleep -Milliseconds 800
}
$rect = New-Object Win32+RECT
[Win32]::GetWindowRect($h, [ref]$rect) | Out-Null
$w = $rect.Right - $rect.Left
$ht = $rect.Bottom - $rect.Top
Write-Output ("window {0}x{1} at {2},{3}" -f $w, $ht, $rect.Left, $rect.Top)
if ($w -gt 100 -and $ht -gt 100) {
$bmp = New-Object System.Drawing.Bitmap($w, $ht)
$g = [System.Drawing.Graphics]::FromImage($bmp)
$g.CopyFromScreen($rect.Left, $rect.Top, 0, 0, $bmp.Size)
$out = "$env:TEMP\vibe-installed-window.png"
$bmp.Save($out, [System.Drawing.Imaging.ImageFormat]::Png)
$g.Dispose(); $bmp.Dispose()
Write-Output ("saved " + $out)
} else { Write-Output "still hidden - non-interactive session" }
Stop-Process -Name VibeClient -Force -ErrorAction SilentlyContinue
Write-Output killed