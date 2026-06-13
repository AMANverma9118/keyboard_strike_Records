$adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
$apk = Join-Path $PSScriptRoot "app\build\outputs\apk\debug\app-debug.apk"

Write-Host "Checking connected devices..." -ForegroundColor Cyan
& $adb devices -l

$devices = (& $adb devices | Select-String "device$" | ForEach-Object { ($_ -split "\s+")[0] })
if (-not $devices) {
    Write-Host ""
    Write-Host "No phone detected yet." -ForegroundColor Yellow
    Write-Host "On your phone:"
    Write-Host "  1. Settings -> About phone -> tap Build number 7 times"
    Write-Host "  2. Settings -> Developer options -> enable USB debugging"
    Write-Host "  3. Set USB mode to File Transfer (not Charging only)"
    Write-Host "  4. Unplug and replug USB, then tap Allow on the phone prompt"
    Write-Host ""
    Write-Host "Run this script again after the phone appears in adb devices."
    exit 1
}

if (-not (Test-Path $apk)) {
    Write-Host "APK not found. Run .\build-apk.ps1 first." -ForegroundColor Red
    exit 1
}

Write-Host ""
Write-Host "Installing keyboard app..." -ForegroundColor Cyan
& $adb install -r $apk

if ($LASTEXITCODE -eq 0) {
    Write-Host ""
    Write-Host "Installed successfully." -ForegroundColor Green
    Write-Host "Next on your phone:"
    Write-Host "  1. Open Keyword Record Keyboard app"
    Write-Host "  2. Set server URL to: http://192.168.1.5:3000"
    Write-Host "  3. Settings -> Languages & input -> enable this keyboard"
    Write-Host "  4. Open any app, switch keyboard, and type to test"
    Write-Host ""
    Write-Host "Dashboard: http://localhost:3000"
}
