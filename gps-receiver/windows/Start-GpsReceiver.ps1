param(
    [switch]$Migrate,
    [switch]$CreateUser,
    [string]$Username,
    [ValidateSet('admin', 'dispatcher')][string]$Role = 'dispatcher',
    [switch]$Update
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

function Unprotect-Text([string]$Path) {
    $encrypted = [IO.File]::ReadAllBytes($Path)
    $plain = [Security.Cryptography.ProtectedData]::Unprotect(
        $encrypted, $null, [Security.Cryptography.DataProtectionScope]::LocalMachine)
    try { return [Text.Encoding]::UTF8.GetString($plain) }
    finally { [Array]::Clear($plain, 0, $plain.Length) }
}

Get-Content -LiteralPath $env:GPS_ENV_FILE | ForEach-Object {
    if ($_ -and -not $_.StartsWith('#')) {
        $key, $value = $_ -split '=', 2
        [Environment]::SetEnvironmentVariable($key, $value, 'Process')
    }
}
$databasePassword = Unprotect-Text $env:GPS_DATABASE_SECRET_FILE
$env:GPS_SESSION_SECRET = Unprotect-Text $env:GPS_SESSION_SECRET_FILE
$env:GPS_DATABASE_URL = 'postgres://{0}:{1}@{2}:{3}/{4}' -f $env:GPS_DATABASE_USER,
    [Uri]::EscapeDataString($databasePassword), $env:GPS_DATABASE_HOST, $env:GPS_DATABASE_PORT, $env:GPS_DATABASE_NAME
$databasePassword = $null
if ($Migrate -and $CreateUser) { throw 'Choose either -Migrate or -CreateUser.' }
$entrypoint = if ($Migrate) { 'app\scripts\migrate.js' } elseif ($CreateUser) { 'app\scripts\create-user.js' } else { 'app\src\index.js' }
$arguments = @((Join-Path $PSScriptRoot $entrypoint))
if ($CreateUser) {
    if ([string]::IsNullOrWhiteSpace($Username)) { throw '-Username is required with -CreateUser.' }
    $arguments += @('--username', $Username, '--role', $Role, '--password-stdin')
    if ($Update) { $arguments += '--update' }
}
& (Join-Path $PSScriptRoot 'node\node.exe') @arguments
exit $LASTEXITCODE
