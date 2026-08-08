[CmdletBinding(SupportsShouldProcess = $true)]
param(
    [string]$RootPath = 'D:\InternalGPS',
    [string]$NodeArchivePath,
    [string]$WinSWPath
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$ServiceName = 'InternalGpsReceiver'
$FirewallName = 'InternalGpsReceiver-5055'
$NodeHash = 'EC56B84A7551893AB2324EBDFDC4AB974A63B4781162600B68A1293CC3E53765'
$WinSWHash = '05B82D46AD331CC16BDC00DE5C6332C1EF818DF8CEEFCD49C726553209B3A0DA'
$projectRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\..'))
. (Join-Path $PSScriptRoot 'DeploymentPaths.ps1')
$paths = Resolve-DeploymentPaths -RootPath $RootPath
if (-not $NodeArchivePath) { $NodeArchivePath = Join-Path $projectRoot 'server\cache\node-v24.18.1-win-x64.zip' }
if (-not $WinSWPath) { $WinSWPath = Join-Path $projectRoot 'server\cache\WinSW-x64-v2.12.0.exe' }

if ($WhatIfPreference) {
    Write-Host "What if: install $ServiceName under $($paths.Root), preserve data, and open TCP 5055 for Profile 'Any' RemoteAddress 'LocalSubnet'."
    exit 0
}

$identity = [Security.Principal.WindowsIdentity]::GetCurrent()
$principal = New-Object Security.Principal.WindowsPrincipal($identity)
if (-not $principal.IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)) {
    throw 'Run this script from an elevated PowerShell session.'
}
Assert-DeploymentDrive -Paths $paths
if ((Get-FileHash -LiteralPath $NodeArchivePath -Algorithm SHA256).Hash -ne $NodeHash) { throw 'Node.js archive SHA-256 mismatch.' }
if ((Get-FileHash -LiteralPath $WinSWPath -Algorithm SHA256).Hash -ne $WinSWHash) { throw 'WinSW SHA-256 mismatch.' }
if (Get-NetTCPConnection -LocalPort 5055 -State Listen -ErrorAction SilentlyContinue) {
    $existing = Get-Service -Name $ServiceName -ErrorAction SilentlyContinue
    if (-not $existing) { throw 'TCP port 5055 is already used by another process.' }
}

$wrapper = Join-Path $paths.ReceiverRoot 'InternalGpsReceiver.exe'
$config = Join-Path $paths.ReceiverRoot 'InternalGpsReceiver.xml'
$dataDir = Join-Path $paths.ReceiverDataRoot 'data'
$logDir = Join-Path $paths.ReceiverDataRoot 'logs'
$environmentFile = Join-Path $paths.ReceiverDataRoot 'config\receiver.env'
if (-not (Test-Path -LiteralPath $environmentFile)) { throw 'Run Install-FleetDatabase.ps1 before installing the receiver service.' }
$service = Get-Service -Name $ServiceName -ErrorAction SilentlyContinue
if ($service -and $service.Status -ne 'Stopped') {
    & $wrapper stop
    Start-Sleep -Seconds 2
}

New-Item -ItemType Directory -Force -Path $paths.ReceiverRoot, $paths.ReceiverDataRoot, $paths.BackupRoot, $dataDir, $logDir | Out-Null
& icacls.exe $paths.ReceiverDataRoot /inheritance:r /grant:r 'NT AUTHORITY\LOCAL SERVICE:(OI)(CI)M' 'BUILTIN\Administrators:(OI)(CI)F' 'SYSTEM:(OI)(CI)F' | Out-Null

$nodeTemp = Join-Path $env:TEMP 'internal-gps-node-extract'
if (Test-Path -LiteralPath $nodeTemp) { Remove-Item -LiteralPath $nodeTemp -Recurse -Force }
Expand-Archive -LiteralPath $NodeArchivePath -DestinationPath $nodeTemp -Force
$nodeSource = Get-ChildItem -LiteralPath $nodeTemp -Directory | Select-Object -First 1
if (-not $nodeSource -or -not (Test-Path (Join-Path $nodeSource.FullName 'node.exe'))) { throw 'Node.js archive layout is invalid.' }
$nodeTarget = Join-Path $paths.ReceiverRoot 'node'
if (Test-Path -LiteralPath $nodeTarget) { Remove-Item -LiteralPath $nodeTarget -Recurse -Force }
New-Item -ItemType Directory -Path $nodeTarget | Out-Null
Copy-Item -Path (Join-Path $nodeSource.FullName '*') -Destination $nodeTarget -Recurse -Force
Remove-Item -LiteralPath $nodeTemp -Recurse -Force

$appTarget = Join-Path $paths.ReceiverRoot 'app'
if (Test-Path -LiteralPath $appTarget) { Remove-Item -LiteralPath $appTarget -Recurse -Force }
New-Item -ItemType Directory -Path $appTarget | Out-Null
Copy-Item -LiteralPath (Join-Path $projectRoot 'gps-receiver\src') -Destination $appTarget -Recurse -Force
Copy-Item -LiteralPath (Join-Path $projectRoot 'gps-receiver\scripts') -Destination $appTarget -Recurse -Force
Copy-Item -LiteralPath (Join-Path $projectRoot 'gps-receiver\package.json') -Destination $appTarget -Force
Copy-Item -LiteralPath (Join-Path $projectRoot 'gps-receiver\package-lock.json') -Destination $appTarget -Force
Copy-Item -LiteralPath $WinSWPath -Destination $wrapper -Force
Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'Start-GpsReceiver.ps1') -Destination $paths.ReceiverRoot -Force

$xml = Get-Content -Raw -Encoding UTF8 (Join-Path $PSScriptRoot 'InternalGpsReceiver.xml.template')
$xml = $xml.Replace('@@DATA_DIR@@', [Security.SecurityElement]::Escape($dataDir))
$xml = $xml.Replace('@@LOG_DIR@@', [Security.SecurityElement]::Escape($logDir))
$xml = $xml.Replace('@@ENV_FILE@@', [Security.SecurityElement]::Escape($environmentFile))
[IO.File]::WriteAllText($config, $xml, (New-Object Text.UTF8Encoding($false)))

Push-Location $appTarget
try {
    & (Join-Path $nodeTarget 'npm.cmd') ci --omit=dev --no-audit --no-fund
    if ($LASTEXITCODE -ne 0) { throw "npm ci failed with exit code $LASTEXITCODE." }
} finally { Pop-Location }
& (Join-Path $paths.ReceiverRoot 'Start-GpsReceiver.ps1') -EnvironmentFile $environmentFile -Migrate
if ($LASTEXITCODE -ne 0) { throw "Database migration failed with exit code $LASTEXITCODE." }

if (-not $service) {
    & $wrapper install
    if ($LASTEXITCODE -ne 0) { throw "WinSW install failed with exit code $LASTEXITCODE." }
}

$rule = Get-NetFirewallRule -DisplayName $FirewallName -ErrorAction SilentlyContinue
if ($rule) { Remove-NetFirewallRule -DisplayName $FirewallName }
New-NetFirewallRule -DisplayName $FirewallName -Direction Inbound -Action Allow -Protocol TCP -LocalPort 5055 -Profile 'Any' -RemoteAddress 'LocalSubnet' | Out-Null
& $wrapper start
if ($LASTEXITCODE -ne 0) { throw "WinSW start failed with exit code $LASTEXITCODE." }
Write-Host "$ServiceName installed and started on TCP 5055."
