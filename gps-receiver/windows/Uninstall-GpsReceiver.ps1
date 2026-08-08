[CmdletBinding(SupportsShouldProcess = $true, ConfirmImpact = 'High')]
param(
    [switch]$PurgeData,
    [string]$RootPath = 'D:\InternalGPS'
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'DeploymentPaths.ps1')
$paths = Resolve-DeploymentPaths -RootPath $RootPath
$wrapper = Join-Path $paths.ReceiverRoot 'InternalGpsReceiver.exe'

if (Test-Path -LiteralPath $wrapper) {
    if ($PSCmdlet.ShouldProcess('InternalGpsReceiver', 'Stop and uninstall Windows service')) {
        & $wrapper stop 2>$null
        & $wrapper uninstall
    }
}
Get-NetFirewallRule -DisplayName 'InternalGpsReceiver-5055' -ErrorAction SilentlyContinue | Remove-NetFirewallRule
if ((Test-Path -LiteralPath $paths.ReceiverRoot) -and $PSCmdlet.ShouldProcess($paths.ReceiverRoot, 'Delete receiver binaries')) {
    Remove-Item -LiteralPath $paths.ReceiverRoot -Recurse -Force
}

if ($PurgeData) {
    if ($paths.Root -ne 'D:\InternalGPS') { throw 'Purge is allowed only for the exact D:\InternalGPS deployment root.' }
    if (Test-Path -LiteralPath $paths.Root) {
        $rootItem = Get-Item -LiteralPath $paths.Root -Force
        if (($rootItem.Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0) {
            throw 'Refusing to purge a reparse point.'
        }
    }
    if ((Test-Path -LiteralPath $paths.ReceiverDataRoot) -and $PSCmdlet.ShouldProcess($paths.ReceiverDataRoot, 'Permanently delete receiver data, logs and secrets')) {
        Remove-Item -LiteralPath $paths.ReceiverDataRoot -Recurse -Force
    }
}

Write-Host "PostgreSQL data preserved at $($paths.PostgresDataRoot)."
Write-Host "Backup preserved at $($paths.BackupRoot)."
if (-not $PurgeData) { Write-Host "Receiver data preserved at $($paths.ReceiverDataRoot)." }
