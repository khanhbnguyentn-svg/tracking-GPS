Set-StrictMode -Version Latest

$script:ConfigKeys = @(
    'LanAddress', 'LanPrefixLength', 'AdminRemoteAddress',
    'PublicGpsPort', 'PublicWebPort', 'TraccarGpsPort', 'TraccarWebPort', 'PostgresPort',
    'InstallRoot', 'DataRoot', 'BackupRoot', 'BackupRetentionDays', 'DiskWarningPercent'
)

function Test-IPv4Address {
    param([Parameter(Mandatory = $true)][string]$Value)

    $parsed = $null
    return [System.Net.IPAddress]::TryParse($Value, [ref]$parsed) -and
        $parsed.AddressFamily -eq [System.Net.Sockets.AddressFamily]::InterNetwork
}

function Test-ManagedPath {
    param(
        [Parameter(Mandatory = $true)][string]$Name,
        [Parameter(Mandatory = $true)][string]$Value
    )

    if (-not [System.IO.Path]::IsPathRooted($Value)) {
        throw "$Name must be an absolute path."
    }

    $fullPath = [System.IO.Path]::GetFullPath($Value).TrimEnd('\')
    $pathRoot = [System.IO.Path]::GetPathRoot($fullPath).TrimEnd('\')
    if ($fullPath -eq $pathRoot) {
        throw "$Name must not be a drive root."
    }
}

function Test-ServerConfig {
    [CmdletBinding()]
    param([Parameter(Mandatory = $true)][hashtable]$Config)

    foreach ($key in $Config.Keys) {
        if ($script:ConfigKeys -notcontains $key) {
            throw "Unknown configuration key: $key"
        }
    }
    foreach ($key in $script:ConfigKeys) {
        if (-not $Config.ContainsKey($key)) {
            throw "Missing configuration key: $key"
        }
    }

    if (-not (Test-IPv4Address -Value ([string]$Config.LanAddress))) {
        throw 'LanAddress must be a valid IPv4 address.'
    }
    if ([int]$Config.LanPrefixLength -lt 1 -or [int]$Config.LanPrefixLength -gt 32) {
        throw 'LanPrefixLength must be between 1 and 32.'
    }
    if ([string]$Config.AdminRemoteAddress -notmatch '^(?:\d{1,3}\.){3}\d{1,3}/(?:[1-9]|[12][0-9]|3[0-2])$') {
        throw 'AdminRemoteAddress must be an IPv4 CIDR range.'
    }

    $portKeys = @('PublicGpsPort', 'PublicWebPort', 'TraccarGpsPort', 'TraccarWebPort', 'PostgresPort')
    $ports = @()
    foreach ($key in $portKeys) {
        $port = [int]$Config[$key]
        if ($port -lt 1 -or $port -gt 65535) {
            throw "$key must be between 1 and 65535."
        }
        $ports += $port
    }
    if (($ports | Select-Object -Unique).Count -ne $ports.Count) {
        throw 'All configured ports must be unique.'
    }

    foreach ($key in @('InstallRoot', 'DataRoot', 'BackupRoot')) {
        Test-ManagedPath -Name $key -Value ([string]$Config[$key])
    }
    if ([int]$Config.BackupRetentionDays -lt 7 -or [int]$Config.BackupRetentionDays -gt 365) {
        throw 'BackupRetentionDays must be between 7 and 365.'
    }
    if ([int]$Config.DiskWarningPercent -lt 50 -or [int]$Config.DiskWarningPercent -gt 95) {
        throw 'DiskWarningPercent must be between 50 and 95.'
    }
}

function Read-ServerConfig {
    [CmdletBinding()]
    param([Parameter(Mandatory = $true)][string]$Path)

    if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) {
        throw "Configuration file not found: $Path"
    }
    $config = Import-PowerShellDataFile -LiteralPath $Path
    Test-ServerConfig -Config $config
    return $config
}

$privateFiles = Get-ChildItem -LiteralPath (Join-Path $PSScriptRoot 'Private') -Filter '*.ps1' -ErrorAction SilentlyContinue
foreach ($privateFile in $privateFiles) {
    . $privateFile.FullName
}

Export-ModuleMember -Function @(
    'Read-ServerConfig', 'Test-ServerConfig', 'New-TraccarConfig', 'New-CaddyConfig',
    'Test-ArtifactDefinition', 'Complete-VerifiedArtifact', 'Get-VerifiedArtifact',
    'Get-InstallPlan', 'Get-TraccarResourceNames'
)
