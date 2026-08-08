[CmdletBinding(SupportsShouldProcess = $true)]
param(
    [string]$RootPath = 'D:\InternalGPS',
    [string]$PostgisArchivePath,
    [string]$NativeInstallerPath
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$ServiceName = 'InternalTraccar-PostgreSQL'
$PostgisHash = '7BA180EE2A352987B9A2F194673652C59483B55852295CCF401DCECCD8765425'
$projectRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\..'))
. (Join-Path $PSScriptRoot 'DeploymentPaths.ps1')
$paths = Resolve-DeploymentPaths -RootPath $RootPath
if (-not $PostgisArchivePath) { $PostgisArchivePath = Join-Path $projectRoot 'server\cache\postgis-bundle-pg17-3.6.2x64.zip' }
if (-not $NativeInstallerPath) { $NativeInstallerPath = Join-Path $projectRoot 'server\scripts\Install-NativePostgres.ps1' }

if ($WhatIfPreference) {
    Write-Host "What if: prepare PostgreSQL 17.10 and PostGIS 3.6.2 on 127.0.0.1:5432 under $($paths.Root)."
    exit 0
}

function Assert-Administrator {
    $identity = [Security.Principal.WindowsIdentity]::GetCurrent()
    $principal = New-Object Security.Principal.WindowsPrincipal($identity)
    if (-not $principal.IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)) {
        throw 'Run this script from an elevated PowerShell session.'
    }
}

function New-HexSecret {
    $bytes = New-Object byte[] 32
    [Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($bytes)
    return (($bytes | ForEach-Object { $_.ToString('x2') }) -join '')
}

function Protect-Text([string]$Value, [string]$Path) {
    $plain = [Text.Encoding]::UTF8.GetBytes($Value)
    try {
        $encrypted = [Security.Cryptography.ProtectedData]::Protect(
            $plain, $null, [Security.Cryptography.DataProtectionScope]::LocalMachine)
        [IO.File]::WriteAllBytes($Path, $encrypted)
    } finally {
        [Array]::Clear($plain, 0, $plain.Length)
    }
}

function Unprotect-CurrentUserText([string]$Path) {
    $secure = Get-Content -Raw -LiteralPath $Path | ConvertTo-SecureString
    $pointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure)
    try { return [Runtime.InteropServices.Marshal]::PtrToStringBSTR($pointer) }
    finally { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($pointer) }
}

Assert-Administrator
Assert-DeploymentDrive -Paths $paths
if (-not (Get-Service -Name $ServiceName -ErrorAction SilentlyContinue)) {
    & $NativeInstallerPath -InstallRoot $paths.PostgresRoot -DataRoot $paths.PostgresDataRoot -ServiceName $ServiceName
    if ($LASTEXITCODE -ne 0) { throw "PostgreSQL 17.10 installer failed with exit code $LASTEXITCODE." }
}

$postgisControl = Join-Path $paths.PostgresRoot 'share\extension\postgis.control'
if (-not (Test-Path -LiteralPath $postgisControl)) {
    if ((Get-FileHash -LiteralPath $PostgisArchivePath -Algorithm SHA256).Hash -ne $PostgisHash) {
        throw 'PostGIS 3.6.2 artifact SHA-256 mismatch.'
    }
    $temp = Join-Path $env:TEMP 'internal-gps-postgis-extract'
    if (Test-Path -LiteralPath $temp) { Remove-Item -LiteralPath $temp -Recurse -Force }
    try {
        Expand-Archive -LiteralPath $PostgisArchivePath -DestinationPath $temp -Force
        $source = Get-ChildItem -LiteralPath $temp -Directory | Select-Object -First 1
        if (-not $source -or -not (Test-Path (Join-Path $source.FullName 'share\extension\postgis.control'))) {
            throw 'PostGIS archive layout is invalid.'
        }
        Copy-Item -Path (Join-Path $source.FullName '*') -Destination $paths.PostgresRoot -Recurse -Force
    } finally {
        Remove-Item -LiteralPath $temp -Recurse -Force -ErrorAction SilentlyContinue
    }
}

$secretsRoot = Join-Path $paths.ReceiverDataRoot 'secrets'
$configRoot = Join-Path $paths.ReceiverDataRoot 'config'
$databaseSecretPath = Join-Path $secretsRoot 'fleet-db.dpapi'
$sessionSecretPath = Join-Path $secretsRoot 'fleet-session.dpapi'
$environmentPath = Join-Path $configRoot 'receiver.env'
New-Item -ItemType Directory -Force -Path $paths.ReceiverDataRoot, $paths.BackupRoot, $secretsRoot, $configRoot | Out-Null
& icacls.exe $paths.ReceiverDataRoot /inheritance:r /grant:r 'NT AUTHORITY\LOCAL SERVICE:(OI)(CI)R' 'BUILTIN\Administrators:(OI)(CI)F' 'SYSTEM:(OI)(CI)F' | Out-Null

$fleetPassword = if (Test-Path -LiteralPath $databaseSecretPath) { $null } else { New-HexSecret }
$adminSecretPath = Join-Path $paths.PostgresDataRoot 'secrets\postgres.dpapi'
$env:PGPASSWORD = Unprotect-CurrentUserText $adminSecretPath
try {
    $psql = Join-Path $paths.PostgresRoot 'bin\psql.exe'
    $createdb = Join-Path $paths.PostgresRoot 'bin\createdb.exe'
    $passwordEncryption = (& $psql -h 127.0.0.1 -p 5432 -U postgres -d postgres -tAc 'SHOW password_encryption').Trim()
    if ($passwordEncryption -ne 'scram-sha-256') { throw 'PostgreSQL must use scram-sha-256.' }
    $roleExists = (& $psql -h 127.0.0.1 -p 5432 -U postgres -d postgres -tAc "SELECT 1 FROM pg_roles WHERE rolname='fleet_app'").Trim()
    if ($roleExists -ne '1') {
        "CREATE ROLE fleet_app LOGIN PASSWORD '$fleetPassword';" | & $psql -h 127.0.0.1 -p 5432 -U postgres -d postgres -v ON_ERROR_STOP=1
        if ($LASTEXITCODE -ne 0) { throw 'Creating fleet_app failed.' }
        Protect-Text $fleetPassword $databaseSecretPath
    } elseif (-not (Test-Path -LiteralPath $databaseSecretPath)) {
        throw 'fleet_app exists but its protected local credential is missing.'
    }
    $databaseExists = (& $psql -h 127.0.0.1 -p 5432 -U postgres -d postgres -tAc "SELECT 1 FROM pg_database WHERE datname='fleet_tracking'").Trim()
    if ($databaseExists -ne '1') {
        & $createdb -h 127.0.0.1 -p 5432 -U postgres -O fleet_app fleet_tracking
        if ($LASTEXITCODE -ne 0) { throw 'Creating fleet_tracking failed.' }
    }
    'CREATE EXTENSION IF NOT EXISTS postgis;' | & $psql -h 127.0.0.1 -p 5432 -U postgres -d fleet_tracking -v ON_ERROR_STOP=1
    if ($LASTEXITCODE -ne 0) { throw 'Enabling PostGIS failed.' }
    $postgisVersion = (& $psql -h 127.0.0.1 -p 5432 -U postgres -d fleet_tracking -tAc 'SELECT postgis_lib_version()').Trim()
    if (-not $postgisVersion.StartsWith('3.6.2')) { throw "Expected PostGIS 3.6.2, found $postgisVersion." }
} finally {
    Remove-Item Env:PGPASSWORD -ErrorAction SilentlyContinue
    $fleetPassword = $null
}

if (-not (Test-Path -LiteralPath $sessionSecretPath)) { Protect-Text (New-HexSecret) $sessionSecretPath }
@"
GPS_HOST=0.0.0.0
GPS_PORT=5055
GPS_DATABASE_HOST=127.0.0.1
GPS_DATABASE_PORT=5432
GPS_DATABASE_NAME=fleet_tracking
GPS_DATABASE_USER=fleet_app
GPS_DATABASE_SECRET_FILE=$databaseSecretPath
GPS_SESSION_SECRET_FILE=$sessionSecretPath
"@ | Set-Content -LiteralPath $environmentPath -Encoding ASCII
Write-Host 'Fleet database and protected receiver configuration are ready.'
