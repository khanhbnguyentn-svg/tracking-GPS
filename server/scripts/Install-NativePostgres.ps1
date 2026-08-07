[CmdletBinding(SupportsShouldProcess = $true)]
param(
    [string]$ArtifactPath,
    [string]$InstallRoot = 'C:\Program Files\InternalTraccar\PostgreSQL',
    [string]$DataRoot = 'C:\ProgramData\InternalTraccar',
    [string]$ServiceName = 'InternalTraccar-PostgreSQL'
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
if ([string]::IsNullOrWhiteSpace($ArtifactPath)) {
    $ArtifactPath = Join-Path $PSScriptRoot '..\cache\postgresql-17.10-windows-x64-binaries.zip'
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

if ($WhatIfPreference) {
    Write-Host "What if: install PostgreSQL 17.10 into $InstallRoot, keep data under $DataRoot, and register $ServiceName."
    exit 0
}
Assert-Administrator
$artifact = [IO.Path]::GetFullPath($ArtifactPath)
$expectedHash = 'EF9B1E5E23D2E8A83914BA13D9DC536A72210FBA53FD1808FF1F7E06BB22B106'
if ((Get-FileHash -LiteralPath $artifact -Algorithm SHA256).Hash -ne $expectedHash) {
    throw 'PostgreSQL artifact SHA-256 mismatch.'
}

$postgresData = Join-Path $DataRoot 'postgres-data'
$secretsRoot = Join-Path $DataRoot 'secrets'
$postgresSecretPath = Join-Path $secretsRoot 'postgres.dpapi'
$traccarSecretPath = Join-Path $secretsRoot 'traccar-db.dpapi'

if (Get-Service -Name $ServiceName -ErrorAction SilentlyContinue) {
    Write-Host "$ServiceName already exists; no changes made."
    exit 0
}

if ($PSCmdlet.ShouldProcess($InstallRoot, 'Install PostgreSQL 17.10 and register Windows service')) {
    New-Item -ItemType Directory -Force -Path $InstallRoot, $DataRoot, $secretsRoot | Out-Null
    $extractRoot = Join-Path $env:TEMP 'internal-traccar-postgres-extract'
    if (Test-Path -LiteralPath $extractRoot) { Remove-Item -LiteralPath $extractRoot -Recurse -Force }
    Expand-Archive -LiteralPath $artifact -DestinationPath $extractRoot -Force
    $source = Join-Path $extractRoot 'pgsql'
    if (-not (Test-Path -LiteralPath (Join-Path $source 'bin\postgres.exe'))) {
        throw 'PostgreSQL archive has an unexpected layout.'
    }
    Copy-Item -Path (Join-Path $source '*') -Destination $InstallRoot -Recurse -Force
    Remove-Item -LiteralPath $extractRoot -Recurse -Force

    $postgresPassword = New-HexSecret
    $traccarPassword = New-HexSecret
    ConvertTo-SecureString $postgresPassword -AsPlainText -Force | ConvertFrom-SecureString |
        Set-Content -LiteralPath $postgresSecretPath -Encoding ASCII
    ConvertTo-SecureString $traccarPassword -AsPlainText -Force | ConvertFrom-SecureString |
        Set-Content -LiteralPath $traccarSecretPath -Encoding ASCII
    & icacls.exe $secretsRoot /inheritance:r /grant:r "$env:USERNAME:(OI)(CI)F" 'SYSTEM:(OI)(CI)F' | Out-Null

    $passwordFile = Join-Path $DataRoot 'initdb-password.tmp'
    try {
        [IO.File]::WriteAllText($passwordFile, $postgresPassword, (New-Object Text.UTF8Encoding($false)))
        & (Join-Path $InstallRoot 'bin\initdb.exe') -D $postgresData -U postgres --pwfile=$passwordFile --encoding=UTF8 --auth-host=scram-sha-256 --auth-local=scram-sha-256
        if ($LASTEXITCODE -ne 0) { throw "initdb failed with exit code $LASTEXITCODE." }
    } finally {
        Remove-Item -LiteralPath $passwordFile -Force -ErrorAction SilentlyContinue
    }

    Add-Content -LiteralPath (Join-Path $postgresData 'postgresql.conf') -Encoding ASCII -Value @"
listen_addresses = '127.0.0.1'
port = 5432
password_encryption = 'scram-sha-256'
"@
    & icacls.exe $postgresData /grant 'NT AUTHORITY\NETWORK SERVICE:(OI)(CI)M' | Out-Null
    & (Join-Path $InstallRoot 'bin\pg_ctl.exe') register -N $ServiceName -D $postgresData -S auto -U 'NT AUTHORITY\NETWORK SERVICE'
    if ($LASTEXITCODE -ne 0) { throw "pg_ctl register failed with exit code $LASTEXITCODE." }
    Start-Service -Name $ServiceName

    $env:PGPASSWORD = $postgresPassword
    try {
        $sql = "CREATE ROLE traccar LOGIN PASSWORD '$traccarPassword';"
        & (Join-Path $InstallRoot 'bin\psql.exe') -h 127.0.0.1 -p 5432 -U postgres -d postgres -v ON_ERROR_STOP=1 -c $sql
        if ($LASTEXITCODE -ne 0) { throw "Creating the Traccar database role failed with exit code $LASTEXITCODE." }
        & (Join-Path $InstallRoot 'bin\createdb.exe') -h 127.0.0.1 -p 5432 -U postgres -O traccar traccar
        if ($LASTEXITCODE -ne 0) { throw "Creating the Traccar database failed with exit code $LASTEXITCODE." }
    } finally {
        Remove-Item Env:PGPASSWORD -ErrorAction SilentlyContinue
        $postgresPassword = $null
        $traccarPassword = $null
    }
    Write-Host 'PostgreSQL service and Traccar database are ready.'
}
