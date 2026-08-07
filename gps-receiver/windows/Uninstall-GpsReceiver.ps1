[CmdletBinding(SupportsShouldProcess = $true, ConfirmImpact = 'High')]
param(
    [switch]$PurgeData,
    [string]$InstallRoot = 'C:\Program Files\InternalGpsReceiver',
    [string]$DataRoot = 'C:\ProgramData\InternalGpsReceiver'
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$wrapper = Join-Path $InstallRoot 'InternalGpsReceiver.exe'
if (Test-Path -LiteralPath $wrapper) {
    & $wrapper stop 2>$null
    & $wrapper uninstall
}
Get-NetFirewallRule -DisplayName 'InternalGpsReceiver-5055' -ErrorAction SilentlyContinue | Remove-NetFirewallRule
if (Test-Path -LiteralPath $InstallRoot) { Remove-Item -LiteralPath $InstallRoot -Recurse -Force }
if ($PurgeData) {
    $dataTarget = [IO.Path]::GetFullPath($DataRoot).TrimEnd('\')
    if ($dataTarget -ne 'C:\ProgramData\InternalGpsReceiver') { throw 'Refusing to purge an unexpected data path.' }
    if ($PSCmdlet.ShouldProcess($dataTarget, 'Permanently delete GPS data and logs')) {
        Remove-Item -LiteralPath $dataTarget -Recurse -Force
    }
} else {
    Write-Host "Service removed. Data preserved at $DataRoot."
}
