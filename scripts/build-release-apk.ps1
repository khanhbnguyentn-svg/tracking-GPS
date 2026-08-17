[CmdletBinding()]
param(
    [string]$GradlePath,
    [string]$AaptPath,
    [string]$ApkSignerPath,
    [string]$OutputDirectory
)

$ErrorActionPreference = 'Stop'
$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
if ([string]::IsNullOrWhiteSpace($GradlePath)) {
    $GradlePath = Join-Path $projectRoot '.tools\gradle-8.13\bin\gradle.bat'
}
if ([string]::IsNullOrWhiteSpace($OutputDirectory)) {
    $OutputDirectory = Join-Path $projectRoot 'dist'
}
$buildToolsRoot = Join-Path $projectRoot '.tools\android-sdk\build-tools'
$latestBuildTools = Get-ChildItem -LiteralPath $buildToolsRoot -Directory |
    Sort-Object { [Version]$_.Name } -Descending | Select-Object -First 1
if (-not $latestBuildTools) {
    throw "Android Build Tools were not found: $buildToolsRoot"
}
if ([string]::IsNullOrWhiteSpace($AaptPath)) {
    $AaptPath = Join-Path $latestBuildTools.FullName 'aapt.exe'
}
if ([string]::IsNullOrWhiteSpace($ApkSignerPath)) {
    $ApkSignerPath = Join-Path $latestBuildTools.FullName 'apksigner.bat'
}

$env:JAVA_HOME = Join-Path $projectRoot '.tools\jdk-17.0.20+8'
$env:GRADLE_USER_HOME = Join-Path $projectRoot '.tools\gradle-home'
$env:ANDROID_USER_HOME = Join-Path $projectRoot '.tools\android-home'
$modulePath = Join-Path $PSScriptRoot 'release\ReleaseTools.psm1'
Import-Module $modulePath -Force -DisableNameChecking

$previousErrorActionPreference = $ErrorActionPreference
try {
    $ErrorActionPreference = 'Continue'
    Push-Location $projectRoot
    $gradleOutput = & $GradlePath :app:testDebugUnitTest :app:lintDebug :app:assembleRelease `
        --offline --no-daemon 2>&1
    $gradleExitCode = $LASTEXITCODE
} finally {
    Pop-Location
    $ErrorActionPreference = $previousErrorActionPreference
}
if ($gradleExitCode -ne 0) {
    throw "Release verification build failed: $($gradleOutput -join ' ')"
}

$sourceApk = Join-Path $projectRoot 'app\build\outputs\apk\release\app-release.apk'
$result = Publish-VerifiedApk -AaptPath $AaptPath -ApkSignerPath $ApkSignerPath `
    -SourceApkPath $sourceApk -OutputDirectory $OutputDirectory `
    -ExpectedPackage 'com.internal.tracker' -ExpectedVersionCode '5' `
    -ExpectedVersionName '2.0.3' `
    -ExpectedFingerprint '8F:19:12:A3:4E:D2:CB:9D:DF:88:40:DB:49:A7:69:13:42:51:B3:29:74:84:33:36:78:E2:C6:79:CA:E4:F5:85'

Write-Output "Release APK: $($result.ApkPath)"
Write-Output "Package: $($result.PackageName)"
Write-Output "Version: $($result.VersionName) ($($result.VersionCode))"
Write-Output "Certificate SHA-256: $($result.Fingerprint)"
