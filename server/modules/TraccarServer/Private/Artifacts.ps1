function Test-ArtifactDefinition {
    [CmdletBinding()]
    param([Parameter(Mandatory = $true)][hashtable]$Definition)

    foreach ($key in @('Url', 'Sha256', 'FileName')) {
        if (-not $Definition.ContainsKey($key) -or [string]::IsNullOrWhiteSpace([string]$Definition[$key])) {
            throw "Artifact definition is missing $key."
        }
    }
    $uri = $null
    if (-not [Uri]::TryCreate([string]$Definition.Url, [UriKind]::Absolute, [ref]$uri) -or $uri.Scheme -ne 'https') {
        throw 'Artifact URL must use HTTPS.'
    }
    if ([string]$Definition.Sha256 -notmatch '^[A-Fa-f0-9]{64}$') {
        throw 'Artifact Sha256 must contain exactly 64 hexadecimal characters.'
    }
    if ([IO.Path]::GetFileName([string]$Definition.FileName) -ne [string]$Definition.FileName) {
        throw 'Artifact FileName must be a plain file name.'
    }
}

function Complete-VerifiedArtifact {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory = $true)][string]$PartialPath,
        [Parameter(Mandatory = $true)][string]$FinalPath,
        [Parameter(Mandatory = $true)][string]$ExpectedSha256
    )

    $actual = (Get-FileHash -LiteralPath $PartialPath -Algorithm SHA256).Hash
    if ($actual -ne $ExpectedSha256) {
        throw 'Downloaded artifact SHA-256 does not match the manifest.'
    }
    Move-Item -LiteralPath $PartialPath -Destination $FinalPath -Force
    return (Get-Item -LiteralPath $FinalPath)
}

function Get-VerifiedArtifact {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory = $true)][hashtable]$Definition,
        [Parameter(Mandatory = $true)][string]$Destination
    )

    Test-ArtifactDefinition -Definition $Definition
    [IO.Directory]::CreateDirectory($Destination) | Out-Null
    $final = Join-Path $Destination $Definition.FileName
    if (Test-Path -LiteralPath $final) {
        $existingHash = (Get-FileHash -LiteralPath $final -Algorithm SHA256).Hash
        if ($existingHash -eq $Definition.Sha256) { return (Get-Item -LiteralPath $final) }
        throw 'Cached artifact SHA-256 does not match the manifest.'
    }
    $partial = "$final.partial"
    Remove-Item -LiteralPath $partial -Force -ErrorAction SilentlyContinue
    Invoke-WebRequest -UseBasicParsing -Uri $Definition.Url -OutFile $partial
    return Complete-VerifiedArtifact -PartialPath $partial -FinalPath $final -ExpectedSha256 $Definition.Sha256
}

function Get-InstallPlan {
    return @('preflight', 'postgres', 'traccar', 'caddy', 'firewall', 'tasks', 'smoke')
}

function Get-TraccarResourceNames {
    return @{
        CaddyService = 'InternalTraccar-Caddy'
        GpsFirewall = 'InternalTraccar-GPS-HTTPS'
        WebFirewall = 'InternalTraccar-Web-HTTPS'
        BackupTask = 'InternalTraccar-Backup'
        HealthTask = 'InternalTraccar-Health'
        ResumeTask = 'InternalTraccar-ResumeAlerts'
    }
}
