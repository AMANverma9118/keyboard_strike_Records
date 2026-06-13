# Build the Android APK (debug)

Write-Host "Building Keyword Record Keyboard APK..." -ForegroundColor Cyan

$projectRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $projectRoot

if (-not (Test-Path ".\gradlew.bat")) {
    Write-Host ""
    Write-Host "Gradle wrapper not found." -ForegroundColor Yellow
    Write-Host "Open android-keyboard in Android Studio, then use:" -ForegroundColor Yellow
    Write-Host "  Build -> Build Bundle(s) / APK(s) -> Build APK(s)" -ForegroundColor Yellow
    Write-Host ""
    Write-Host "Or install Gradle and run: gradle wrapper" -ForegroundColor Yellow
    exit 1
}

.\gradlew.bat assembleDebug

if ($LASTEXITCODE -eq 0) {
    $apkPath = "app\build\outputs\apk\debug\app-debug.apk"
    Write-Host ""
    Write-Host "APK built successfully:" -ForegroundColor Green
    Write-Host (Join-Path $projectRoot $apkPath)
} else {
    Write-Host "Build failed. Open the project in Android Studio for details." -ForegroundColor Red
    exit $LASTEXITCODE
}
