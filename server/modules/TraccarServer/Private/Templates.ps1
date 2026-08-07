function Get-TemplateText {
    param([Parameter(Mandatory = $true)][string]$Name)

    $path = Join-Path (Split-Path $PSScriptRoot -Parent) "..\..\templates\$Name"
    return [System.IO.File]::ReadAllText([System.IO.Path]::GetFullPath($path))
}

function Complete-Template {
    param(
        [Parameter(Mandatory = $true)][string]$Template,
        [Parameter(Mandatory = $true)][hashtable]$Values
    )

    $result = $Template
    foreach ($key in $Values.Keys) {
        $result = $result.Replace("@@$key@@", [string]$Values[$key])
    }
    if ($result -match '@@[A-Z0-9_]+@@') {
        throw "Template contains unresolved token: $($Matches[0])"
    }
    return $result
}

function New-TraccarConfig {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory = $true)][hashtable]$Config,
        [Parameter(Mandatory = $true)][ValidateNotNullOrEmpty()][string]$DatabaseUser,
        [Parameter(Mandatory = $true)][ValidateNotNullOrEmpty()][string]$DatabasePassword,
        [Parameter(Mandatory = $true)][int]$PendingGroupId
    )

    Test-ServerConfig -Config $Config
    if ($PendingGroupId -le 0) {
        throw 'PendingGroupId must be a positive integer.'
    }
    $escape = [System.Security.SecurityElement]::Escape
    return Complete-Template -Template (Get-TemplateText 'traccar.xml.template') -Values @{
        POSTGRES_PORT = [int]$Config.PostgresPort
        DATABASE_USER = $escape.Invoke($DatabaseUser)
        DATABASE_PASSWORD = $escape.Invoke($DatabasePassword)
        TRACCAR_WEB_PORT = [int]$Config.TraccarWebPort
        TRACCAR_GPS_PORT = [int]$Config.TraccarGpsPort
        PENDING_GROUP_ID = $PendingGroupId
    }
}

function New-CaddyConfig {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory = $true)][hashtable]$Config,
        [Parameter(Mandatory = $true)][ValidateNotNullOrEmpty()][string]$ServerName
    )

    Test-ServerConfig -Config $Config
    $parsedIp = $null
    $isIp = [System.Net.IPAddress]::TryParse($ServerName, [ref]$parsedIp)
    $isHostname = $ServerName -match '^(?=.{1,253}$)(?:[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?\.)*[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?$'
    if (-not $isIp -and -not $isHostname) {
        throw 'ServerName must be a hostname or IPv4 address without scheme, port, or path.'
    }
    return Complete-Template -Template (Get-TemplateText 'Caddyfile.template') -Values @{
        SERVER_NAME = $ServerName
        PUBLIC_GPS_PORT = [int]$Config.PublicGpsPort
        PUBLIC_WEB_PORT = [int]$Config.PublicWebPort
        TRACCAR_GPS_PORT = [int]$Config.TraccarGpsPort
        TRACCAR_WEB_PORT = [int]$Config.TraccarWebPort
    }
}
