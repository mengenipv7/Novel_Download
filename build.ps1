# Auto build script: bump version and output versioned APK
# Usage: powershell -ExecutionPolicy Bypass -File build.ps1

$ErrorActionPreference = "Stop"
$root = $PSScriptRoot
$versionFile = Join-Path $root "version.properties"

# --- 1. Read and bump version ---
$content = [System.IO.File]::ReadAllText($versionFile)
if ($content -notmatch "VERSION_CODE=(\d+)") { throw "version.properties missing VERSION_CODE" }
$code = [int]$Matches[1]
if ($content -notmatch "VERSION_NAME=([\d\.]+)") { throw "version.properties missing VERSION_NAME" }
$name = $Matches[1]

$code = $code + 1
$parts = $name -split "\."
while ($parts.Count -lt 3) { $parts += "0" }
$parts[-1] = [string]([int]$parts[-1] + 1)
$name = $parts -join "."

# write WITHOUT BOM so Java Properties can parse it
$utf8NoBom = New-Object System.Text.UTF8Encoding($false)
[System.IO.File]::WriteAllText($versionFile, "VERSION_CODE=$code`nVERSION_NAME=$name`n", $utf8NoBom)
Write-Host ">>> New version: v$name (build $code)" -ForegroundColor Cyan

# --- 2. Build ---
$gradlew = Join-Path $root "gradlew.bat"
if (-not (Test-Path $gradlew)) { throw "gradlew.bat not found" }

& $gradlew ":app:assembleRelease" --no-daemon
if ($LASTEXITCODE -ne 0) { throw "Gradle build failed, exit code $LASTEXITCODE" }

# --- 3. Copy artifact ---
$apkIn = Join-Path $root "app\build\outputs\apk\release\app-release.apk"
if (-not (Test-Path $apkIn)) { throw "APK not found: $apkIn" }

$outDir = Join-Path $root "builds"
New-Item -ItemType Directory -Force -Path $outDir | Out-Null
$apkOut = Join-Path $outDir "novel-downloader-v$name-b$code.apk"
Copy-Item $apkIn $apkOut -Force
Write-Host ">>> APK output: $apkOut" -ForegroundColor Green
