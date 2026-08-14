[CmdletBinding()]
param(
    [string]$KeyToolPath,
    [string]$SourceKeyStore,
    [string]$SourceStorePassword = 'android',
    [string]$SourceAlias = 'androiddebugkey',
    [string]$DestinationDirectory,
    [string]$ExpectedFingerprint = '8F:19:12:A3:4E:D2:CB:9D:DF:88:40:DB:49:A7:69:13:42:51:B3:29:74:84:33:36:78:E2:C6:79:CA:E4:F5:85'
)

$ErrorActionPreference = 'Stop'
$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
if ([string]::IsNullOrWhiteSpace($KeyToolPath)) {
    $KeyToolPath = Join-Path $projectRoot '.tools\jdk-17.0.20+8\bin\keytool.exe'
}
if ([string]::IsNullOrWhiteSpace($SourceKeyStore)) {
    $SourceKeyStore = Join-Path $projectRoot '.tools\android-home\debug.keystore'
}
if ([string]::IsNullOrWhiteSpace($DestinationDirectory)) {
    $DestinationDirectory = Join-Path $projectRoot '.signing'
}
$modulePath = Join-Path $PSScriptRoot 'release\ReleaseTools.psm1'
Import-Module $modulePath -Force -DisableNameChecking

$result = Initialize-ReleaseSigning -KeyToolPath $KeyToolPath -SourceKeyStore $SourceKeyStore `
    -SourceStorePassword $SourceStorePassword -SourceAlias $SourceAlias `
    -DestinationDirectory $DestinationDirectory -ExpectedFingerprint $ExpectedFingerprint

Write-Output "Release keystore: $($result.KeyStorePath)"
Write-Output "Release alias: $($result.Alias)"
Write-Output "Certificate SHA-256: $($result.Fingerprint)"
