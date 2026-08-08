[CmdletBinding()]
param(
    [ValidateSet('Start', 'Status', 'Stop')]
    [string]$Action,
    [string]$RootPath = 'D:\InternalGPS',
    [string]$CloudflaredPath
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Security

function Find-QuickTunnelUrl([string[]]$Lines) {
    foreach ($line in $Lines) {
        foreach ($match in [regex]::Matches($line, 'https?://[^\s<>"'']+')) {
            $candidate = $match.Value.TrimEnd([char[]]'.,;:!?)]}')
            $uri = $null
            if ([Uri]::TryCreate($candidate, [UriKind]::Absolute, [ref]$uri) -and
                $uri.Scheme -eq 'https' -and
                $uri.Host -match '^[a-z0-9-]+\.trycloudflare\.com$') {
                return $uri
            }
        }
    }
    return $null
}

function Protect-PilotToken([string]$Value, [string]$Path) {
    $plain = [Text.Encoding]::UTF8.GetBytes($Value)
    $encrypted = $null
    try {
        $encrypted = [Security.Cryptography.ProtectedData]::Protect(
            $plain, $null, [Security.Cryptography.DataProtectionScope]::LocalMachine)
        [IO.File]::WriteAllBytes($Path, $encrypted)
    } finally {
        if ($plain) { [Array]::Clear($plain, 0, $plain.Length) }
        if ($encrypted) { [Array]::Clear($encrypted, 0, $encrypted.Length) }
    }
}

function Remove-PilotArtifact([string]$Path) {
    if (Test-Path -LiteralPath $Path) {
        [IO.File]::Delete($Path)
    }
    if (Test-Path -LiteralPath $Path) { throw "Pilot artifact could not be deleted: $Path" }
}

function Write-EnvironmentFile([string]$EnvPath, [string[]]$Lines) {
    if (-not (Test-Path -LiteralPath $EnvPath -PathType Leaf)) {
        throw "Receiver environment file does not exist: $EnvPath"
    }
    $temporaryPath = "$EnvPath.tmp.$([Guid]::NewGuid().ToString('N'))"
    $backupPath = "$EnvPath.bak.$([Guid]::NewGuid().ToString('N'))"
    $content = @($Lines) -join [Environment]::NewLine
    if ($content.Length -gt 0) { $content += [Environment]::NewLine }
    try {
        [IO.File]::WriteAllText($temporaryPath, $content, (New-Object Text.UTF8Encoding($false)))
        [IO.File]::Replace($temporaryPath, $EnvPath, $backupPath)
    } finally {
        Remove-PilotArtifact $temporaryPath
        Remove-PilotArtifact $backupPath
    }
}

function Set-ReceiverPilotSecret([string]$EnvPath, [string]$SecretPath) {
    $lines = @(Get-Content -LiteralPath $EnvPath | Where-Object { $_ -notmatch '^GPS_INGEST_TOKEN_SECRET_FILE=' })
    Write-EnvironmentFile $EnvPath ($lines + "GPS_INGEST_TOKEN_SECRET_FILE=$SecretPath")
}

function Remove-ReceiverPilotSecret([string]$EnvPath) {
    $lines = @(Get-Content -LiteralPath $EnvPath | Where-Object { $_ -notmatch '^GPS_INGEST_TOKEN_SECRET_FILE=' })
    Write-EnvironmentFile $EnvPath $lines
}

function Test-PilotProcessRecord([pscustomobject]$State, [string]$ExpectedExe, [pscustomobject]$Process) {
    try {
        $processId = [int]$State.Pid
        if ($processId -le 0 -or [string]::IsNullOrWhiteSpace($State.ExecutablePath)) { return $false }
        $expectedPath = [IO.Path]::GetFullPath($ExpectedExe)
        if (-not $State.ExecutablePath.Equals($expectedPath, [StringComparison]::OrdinalIgnoreCase)) { return $false }
        if (-not $Process -or [string]::IsNullOrWhiteSpace($Process.ExecutablePath) -or
            -not $Process.ExecutablePath.Equals($expectedPath, [StringComparison]::OrdinalIgnoreCase)) {
            return $false
        }
        $commandLine = (($Process.CommandLine -replace '"', '') -replace '\s+', ' ').Trim()
        return $commandLine.EndsWith(
            'tunnel --url http://127.0.0.1:5055 --no-autoupdate',
            [StringComparison]::OrdinalIgnoreCase)
    } catch {
        return $false
    }
}

function Test-PilotProcess([pscustomobject]$State, [string]$ExpectedExe) {
    try {
        $processId = [int]$State.Pid
        $process = Get-CimInstance Win32_Process -Filter "ProcessId = $processId" -ErrorAction Stop
        return Test-PilotProcessRecord $State $ExpectedExe $process
    } catch {
        return $false
    }
}

function Stop-PilotProcessIfMatched([pscustomobject]$State, [string]$ExpectedExe) {
    $processId = [int]$State.Pid
    if ($processId -le 0) { throw 'Pilot state contains an invalid process ID.' }
    $process = Get-CimInstance Win32_Process -Filter "ProcessId = $processId" -ErrorAction Stop
    if (-not $process -or -not (Test-PilotProcessRecord $State $ExpectedExe $process)) { return $false }
    $stopped = Stop-Process -Id $processId -Force -PassThru -ErrorAction Stop
    if (-not $stopped.WaitForExit(15000)) { throw "Quick Tunnel process $processId did not stop within 15 seconds." }
    return $true
}

function Assert-Administrator {
    $identity = [Security.Principal.WindowsIdentity]::GetCurrent()
    $principal = New-Object Security.Principal.WindowsPrincipal($identity)
    if (-not $principal.IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)) {
        throw 'Run this script from an elevated PowerShell session.'
    }
}

function Assert-ReceiverHealth([string]$Uri) {
    $health = Invoke-RestMethod -Uri $Uri -TimeoutSec 10
    if ($health.status -ne 'ok') { throw "Receiver health check failed at $Uri" }
}

function Get-HttpStatus([string]$Uri, [hashtable]$Headers) {
    try {
        $response = Invoke-WebRequest -UseBasicParsing -Uri $Uri -Headers $Headers -TimeoutSec 15
        return [int]$response.StatusCode
    } catch {
        if ($_.Exception.Response) { return [int]$_.Exception.Response.StatusCode }
        throw
    }
}

function Restart-Receiver {
    Restart-Service -Name 'InternalGpsReceiver' -Force
    (Get-Service -Name 'InternalGpsReceiver').WaitForStatus('Running', [TimeSpan]::FromSeconds(30))
    Assert-ReceiverHealth 'http://127.0.0.1:5055/health'
}

function Clear-PilotConfiguration(
    [string]$EnvironmentPath,
    [string]$SecretPath,
    [string]$ProfilePath,
    [string]$StatePath,
    [switch]$ReceiverConfigured,
    [switch]$RetainState
) {
    $failures = New-Object 'Collections.Generic.List[Exception]'
    $environmentCleared = -not $ReceiverConfigured
    if ($ReceiverConfigured) {
        try {
            Remove-ReceiverPilotSecret $EnvironmentPath
            $environmentCleared = $true
        } catch {
            $failures.Add($_.Exception)
        }
    }
    try { Remove-PilotArtifact $ProfilePath } catch { $failures.Add($_.Exception) }
    if ($environmentCleared) {
        try { Remove-PilotArtifact $SecretPath } catch { $failures.Add($_.Exception) }
    }
    if ($ReceiverConfigured) {
        try { Restart-Receiver } catch { $failures.Add($_.Exception) }
    }
    if ($failures.Count -eq 0 -and -not $RetainState) {
        try { Remove-PilotArtifact $StatePath } catch { $failures.Add($_.Exception) }
    }
    if ($failures.Count -gt 0) {
        throw [AggregateException]::new('Quick Tunnel pilot cleanup failed.', $failures.ToArray())
    }
}

function Set-PilotAccess([string]$PilotPath, [string]$SecretPath) {
    $identity = [Security.Principal.WindowsIdentity]::GetCurrent()
    $inherit = [Security.AccessControl.InheritanceFlags]'ContainerInherit, ObjectInherit'
    $allow = [Security.AccessControl.AccessControlType]::Allow
    $none = [Security.AccessControl.PropagationFlags]::None
    $directoryAcl = New-Object Security.AccessControl.DirectorySecurity
    $directoryAcl.SetAccessRuleProtection($true, $false)
    foreach ($sid in @('S-1-5-18', 'S-1-5-32-544', $identity.User.Value)) {
        $rule = New-Object Security.AccessControl.FileSystemAccessRule($sid, 'FullControl', $inherit, $none, $allow)
        $directoryAcl.AddAccessRule($rule)
    }
    Set-Acl -LiteralPath $PilotPath -AclObject $directoryAcl

    if ($SecretPath) {
        $fileAcl = New-Object Security.AccessControl.FileSecurity
        $fileAcl.SetAccessRuleProtection($true, $false)
        foreach ($sid in @('S-1-5-18', 'S-1-5-32-544', $identity.User.Value)) {
            $fileAcl.AddAccessRule((New-Object Security.AccessControl.FileSystemAccessRule($sid, 'FullControl', $allow)))
        }
        $fileAcl.AddAccessRule((New-Object Security.AccessControl.FileSystemAccessRule('S-1-5-19', 'Read', $allow)))
        Set-Acl -LiteralPath $SecretPath -AclObject $fileAcl
    }
}

function Read-PilotState([string]$Path) {
    if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) { throw 'No Quick Tunnel pilot state was found.' }
    return Get-Content -Raw -Encoding UTF8 -LiteralPath $Path | ConvertFrom-Json
}

if ($MyInvocation.InvocationName -eq '.') { return }
if (-not $Action) { throw '-Action is required.' }

. (Join-Path $PSScriptRoot 'DeploymentPaths.ps1')
$paths = Resolve-DeploymentPaths -RootPath $RootPath
if ([IO.Path]::GetPathRoot($paths.Root) -ne 'D:\') { throw 'The pilot root must be on the D drive.' }
$pilotRoot = Join-Path $paths.Root 'Pilot'
$secretPath = Join-Path $paths.ReceiverDataRoot 'secrets\pilot-ingest.dpapi'
$profilePath = Join-Path $pilotRoot 'tracking-pilot-profile.json'
$statePath = Join-Path $pilotRoot 'pilot-state.json'
$logPath = Join-Path $pilotRoot 'cloudflared.log'
$stdoutPath = Join-Path $pilotRoot 'cloudflared-output.log'
$environmentPath = Join-Path $paths.ReceiverDataRoot 'config\receiver.env'
$utf8NoBom = New-Object Text.UTF8Encoding($false)

switch ($Action) {
    'Start' {
        Assert-Administrator
        Assert-DeploymentDrive -Paths $paths
        Assert-ReceiverHealth 'http://127.0.0.1:5055/health'

        if ($CloudflaredPath) {
            $resolvedExe = (Resolve-Path -LiteralPath $CloudflaredPath -ErrorAction Stop).ProviderPath
        } else {
            $resolvedExe = (Get-Command cloudflared.exe -CommandType Application -ErrorAction Stop).Source
        }
        $resolvedExe = [IO.Path]::GetFullPath($resolvedExe)
        if ((Get-AuthenticodeSignature -FilePath $resolvedExe).Status -ne 'Valid') {
            throw 'cloudflared.exe must have a valid Authenticode signature.'
        }
        if (Test-Path -LiteralPath $statePath) {
            $existingState = Read-PilotState $statePath
            if (Test-PilotProcess $existingState $resolvedExe) { throw 'A recorded Quick Tunnel pilot is already running.' }
        }

        New-Item -ItemType Directory -Force -Path $pilotRoot | Out-Null
        Set-PilotAccess $pilotRoot $null
        $tokenBytes = New-Object byte[] 32
        $token = $null
        $headers = $null
        $profile = $null
        $pilotState = $null
        $receiverConfigured = $false
        try {
            $generator = [Security.Cryptography.RandomNumberGenerator]::Create()
            try { $generator.GetBytes($tokenBytes) } finally { $generator.Dispose() }
            $token = [Convert]::ToBase64String($tokenBytes).TrimEnd('=').Replace('+', '-').Replace('/', '_')
            Protect-PilotToken $token $secretPath
            Set-PilotAccess $pilotRoot $secretPath

            $receiverConfigured = $true
            Set-ReceiverPilotSecret $environmentPath $secretPath
            Restart-Receiver

            Remove-PilotArtifact $logPath
            Remove-PilotArtifact $stdoutPath
            $tunnelProcess = Start-Process -FilePath $resolvedExe -ArgumentList @('tunnel', '--url', 'http://127.0.0.1:5055', '--no-autoupdate') -WindowStyle Hidden -RedirectStandardOutput $stdoutPath -RedirectStandardError $logPath -PassThru
            $pilotState = [pscustomobject]@{
                Pid = $tunnelProcess.Id
                ExecutablePath = $resolvedExe
                PublicUrl = $null
            }
            [IO.File]::WriteAllText($statePath, ($pilotState | ConvertTo-Json), $utf8NoBom)

            $deadline = [DateTime]::UtcNow.AddSeconds(60)
            $publicUri = $null
            do {
                if (-not (Test-PilotProcess $pilotState $resolvedExe)) { throw 'The Quick Tunnel process exited before publishing a URL.' }
                $logLines = @()
                if (Test-Path -LiteralPath $logPath) { $logLines += @(Get-Content -LiteralPath $logPath) }
                if (Test-Path -LiteralPath $stdoutPath) { $logLines += @(Get-Content -LiteralPath $stdoutPath) }
                $publicUri = Find-QuickTunnelUrl $logLines
                if (-not $publicUri) { Start-Sleep -Seconds 1 }
            } while (-not $publicUri -and [DateTime]::UtcNow -lt $deadline)
            if (-not $publicUri) { throw 'Quick Tunnel did not publish a valid URL within 60 seconds.' }

            $pilotState.PublicUrl = $publicUri.AbsoluteUri.TrimEnd('/')
            [IO.File]::WriteAllText($statePath, ($pilotState | ConvertTo-Json), $utf8NoBom)
            if ((Get-HttpStatus "$($pilotState.PublicUrl)/" @{}) -ne 401) {
                throw 'The public endpoint accepted a request without authentication.'
            }
            $headers = @{ Authorization = "Bearer $token" }
            if ((Get-HttpStatus "$($pilotState.PublicUrl)/?id=bad" $headers) -ne 400) {
                throw 'The authenticated public endpoint did not reach receiver validation.'
            }

            $profile = [ordered]@{
                version = 2
                name = 'Internet pilot 2 ngay'
                host = $publicUri.Host
                port = 443
                scheme = 'https'
                intervalSeconds = 60
                tlsMode = 'system'
                ingestToken = $token
            }
            [IO.File]::WriteAllText($profilePath, ($profile | ConvertTo-Json), $utf8NoBom)
            Write-Host "URL: $($pilotState.PublicUrl)"
            Write-Host "Profile: $profilePath"
            Write-Host "PID: $($pilotState.Pid)"
            Write-Warning 'Restarting the tunnel changes its public URL and requires a new profile import.'
        } catch {
            $startFailure = $_.Exception
            $rollbackFailures = New-Object 'Collections.Generic.List[Exception]'
            $rollbackFailures.Add($startFailure)
            $retainState = $false
            if ($pilotState) {
                try { Stop-PilotProcessIfMatched $pilotState $resolvedExe | Out-Null }
                catch {
                    $retainState = $true
                    $rollbackFailures.Add($_.Exception)
                }
            }
            try {
                Clear-PilotConfiguration $environmentPath $secretPath $profilePath $statePath `
                    -ReceiverConfigured:$receiverConfigured -RetainState:$retainState
            } catch {
                $rollbackFailures.Add($_.Exception)
            }
            if ($rollbackFailures.Count -gt 1) {
                throw [AggregateException]::new('Quick Tunnel start and rollback failed.', $rollbackFailures.ToArray())
            }
            throw $startFailure
        } finally {
            if ($tokenBytes) { [Array]::Clear($tokenBytes, 0, $tokenBytes.Length) }
            $token = $null
            $headers = $null
            $profile = $null
        }
    }
    'Status' {
        $pilotState = Read-PilotState $statePath
        if (-not (Test-PilotProcess $pilotState $pilotState.ExecutablePath)) { throw 'The recorded Quick Tunnel process is not running.' }
        Assert-ReceiverHealth 'http://127.0.0.1:5055/health'
        Assert-ReceiverHealth "$($pilotState.PublicUrl.TrimEnd('/'))/health"
        Write-Host "URL: $($pilotState.PublicUrl)"
        Write-Host "PID: $($pilotState.Pid)"
        Write-Host "Executable: $($pilotState.ExecutablePath)"
        Write-Host 'Local health: ok'
        Write-Host 'Public health: ok'
    }
    'Stop' {
        Assert-Administrator
        Assert-DeploymentDrive -Paths $paths
        $pilotState = Read-PilotState $statePath
        $stopFailures = New-Object 'Collections.Generic.List[Exception]'
        $processStopped = $false
        try {
            $processStopped = Stop-PilotProcessIfMatched $pilotState $pilotState.ExecutablePath
        } catch {
            $stopFailures.Add($_.Exception)
        }
        try {
            Clear-PilotConfiguration $environmentPath $secretPath $profilePath $statePath `
                -ReceiverConfigured -RetainState:($stopFailures.Count -gt 0)
        } catch {
            $stopFailures.Add($_.Exception)
        }
        if ($stopFailures.Count -gt 0) {
            throw [AggregateException]::new('Quick Tunnel stop or cleanup failed.', $stopFailures.ToArray())
        }
        if ($processStopped) { Write-Host "Stopped PID: $($pilotState.Pid)" }
        else { Write-Host 'Recorded pilot process was already absent or different; no process was stopped.' }
        Write-Host 'Pilot credentials and state removed.'
        Write-Host "Retained log: $logPath"
    }
}
