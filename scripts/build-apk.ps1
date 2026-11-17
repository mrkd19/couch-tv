# PowerShell counterpart to scripts/build-apk.sh for Windows. Builds only the TV home
# (dist\couch-tv-home.apk); use build-apk.sh in Git Bash for the on-TV and sync apps.
#
# Needs JDK 21 and the Android SDK with platform 34 and build tools 34 (docs/SETUP.md, "Building
# the app"). Set JAVA_HOME and ANDROID_HOME for this PowerShell session only, or unpack them into
# build\jdk and build\android-sdk inside the repo (git ignores build\):
#   $env:JAVA_HOME = "C:\path\to\jdk-21"; $env:ANDROID_HOME = "C:\path\to\android-sdk"
#   powershell -ExecutionPolicy Bypass -File scripts\build-apk.ps1
#
# The settings built into the home's own copy come from config.example.json, the same default as
# build-apk.sh, so your private config.json never ends up inside an APK file. apply.sh hands the TV
# your config.json at install time anyway. To build your own settings in on purpose, set
# $env:CONFIG to that file.
$ErrorActionPreference = "Stop"

$ROOT = Split-Path -Parent $PSScriptRoot
if ($env:CONFIG) {
    $CONFIG = $env:CONFIG
    if (-not [System.IO.Path]::IsPathRooted($CONFIG)) { $CONFIG = Join-Path $ROOT $CONFIG }
} else {
    $CONFIG = Join-Path $ROOT "config.example.json"
}
if (-not (Test-Path $CONFIG)) {
    Write-Error "Settings file not found: $CONFIG"
    exit 1
}

if (-not $env:JAVA_HOME) {
    $jdk = Join-Path $ROOT "build\jdk"
    if (Test-Path "$jdk\bin\javac.exe") {
        $env:JAVA_HOME = $jdk
    } else {
        Write-Error "JAVA_HOME is not set and build\jdk not found. Set JAVA_HOME to a JDK 21 (docs/SETUP.md, Building the app)."
        exit 1
    }
}

if (-not $env:ANDROID_HOME) {
    $sdk = Join-Path $ROOT "build\android-sdk"
    if (Test-Path "$sdk\platforms") {
        $env:ANDROID_HOME = $sdk
    } else {
        Write-Error "ANDROID_HOME is not set and build\android-sdk not found. Set ANDROID_HOME to your Android SDK (docs/SETUP.md, Building the app)."
        exit 1
    }
}
if (-not (Test-Path (Join-Path $env:ANDROID_HOME "platforms\android-34"))) {
    Write-Error "No Android platform 34 in $env:ANDROID_HOME. Install platform 34 and build tools 34 (docs/SETUP.md, Building the app)."
    exit 1
}
Write-Host "Settings built in: $CONFIG"

$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"

Write-Host "Using JAVA_HOME: $env:JAVA_HOME"
Write-Host "Using ANDROID_HOME: $env:ANDROID_HOME"

# Run Gradle assembleRelease with org.gradle.java.home explicitly set to override ~/.gradle/gradle.properties
$gradlew = Join-Path $ROOT "tv-home\android\gradlew.bat"
$projectDir = Join-Path $ROOT "tv-home\android"
$javaHomeNorm = $env:JAVA_HOME.Replace("\", "/")

Write-Host "Compiling couch-tv-home.apk with Gradle..."
& $gradlew -p "$projectDir" --no-daemon "-Dorg.gradle.java.home=$javaHomeNorm" assembleRelease "-PconfigJson=$CONFIG"

$builtApk = Join-Path $ROOT "tv-home\android\app\build\outputs\apk\release\app-release.apk"
if (-not (Test-Path $builtApk)) {
    Write-Error "Build failed: output APK not found at $builtApk"
    exit 1
}

$distDir = Join-Path $ROOT "dist"
if (-not (Test-Path $distDir)) {
    New-Item -ItemType Directory -Path $distDir | Out-Null
}

$destApk = Join-Path $distDir "couch-tv-home.apk"
Copy-Item $builtApk $destApk -Force

$hash = (Get-FileHash -Path $destApk -Algorithm SHA256).Hash
$size = (Get-Item $destApk).Length
Write-Host "Successfully built: $destApk"
Write-Host "  Size:   $size bytes"
Write-Host "  SHA256: $hash"
