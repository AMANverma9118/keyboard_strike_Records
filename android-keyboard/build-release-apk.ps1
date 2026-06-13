# Build APK and copy to releases folder for sharing

Write-Host "Building Keyword Record Keyboard APK..." -ForegroundColor Cyan

$projectRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $projectRoot

if (-not (Test-Path ".\gradlew.bat")) {
    Write-Host "Gradle wrapper not found." -ForegroundColor Red
    exit 1
}

.\gradlew.bat assembleDebug

if ($LASTEXITCODE -ne 0) {
    Write-Host "Build failed." -ForegroundColor Red
    exit $LASTEXITCODE
}

$apkSource = Join-Path $projectRoot "app\build\outputs\apk\debug\app-debug.apk"
$releasesDir = Join-Path (Split-Path -Parent $projectRoot) "releases"
$apkDest = Join-Path $releasesDir "keyword-record-keyboard.apk"

New-Item -ItemType Directory -Force -Path $releasesDir | Out-Null
Copy-Item -Path $apkSource -Destination $apkDest -Force

Write-Host ""
Write-Host "APK ready to share:" -ForegroundColor Green
Write-Host $apkDest
Write-Host ""
Write-Host "Before sharing, set your Render URL in:" -ForegroundColor Yellow
Write-Host "  app\src\main\res\values\strings.xml  (default_server_url)"
