$root = Split-Path $PSScriptRoot -Parent

Describe 'Windows service assets' {
    It 'defines a stable service with automatic restart and port 5055' {
        $template = Get-Content -Raw -Encoding UTF8 (Join-Path $root 'windows\InternalGpsReceiver.xml.template')
        $template | Should Match '<id>InternalGpsReceiver</id>'
        $template | Should Match '<startmode>Automatic</startmode>'
        $template | Should Match '<onfailure action="restart"'
        $template | Should Match 'GPS_ENV_FILE'
        (Get-Content -Raw -Encoding UTF8 (Join-Path $root 'windows\Install-FleetDatabase.ps1')) | Should Match 'GPS_PORT=5055'
    }

    It 'limits the firewall rule to LocalSubnet on every Windows network profile' {
        $script = Get-Content -Raw -Encoding UTF8 (Join-Path $root 'windows\Install-GpsReceiver.ps1')
        $script | Should Match "Profile 'Any'"
        $script | Should Match "RemoteAddress 'LocalSubnet'"
    }

    It 'uses project-owned stable resource names' {
        $script = Get-Content -Raw -Encoding UTF8 (Join-Path $root 'windows\Install-GpsReceiver.ps1')
        $script | Should Match "ServiceName = 'InternalGpsReceiver'"
        $script | Should Match "FirewallName = 'InternalGpsReceiver-5055'"
    }

    It 'preserves database, GPS data and backup unless PurgeData is explicitly supplied' {
        $script = Get-Content -Raw -Encoding UTF8 (Join-Path $root 'windows\Uninstall-GpsReceiver.ps1')
        $script | Should Match '\[switch\]\$PurgeData'
        $script | Should Match "RootPath = 'D:\\InternalGPS'"
        $script | Should Match 'DeploymentPaths\.ps1'
        $script | Should Match 'if \(\$PurgeData\)'
        $script | Should Match 'Remove-Item[^\r\n]+\$paths\.ReceiverRoot'
        $script | Should Match 'Remove-Item[^\r\n]+\$paths\.ReceiverDataRoot'
        $script | Should Not Match 'Remove-Item[^\r\n]+\$paths\.(PostgresDataRoot|BackupRoot)'
        $script | Should Match '\$paths\.Root -ne ''D:\\InternalGPS'''
        $script | Should Match 'ReparsePoint'
    }

    It 'pins official Node and WinSW artifacts by SHA-256' {
        $script = Get-Content -Raw -Encoding UTF8 (Join-Path $root 'windows\Install-GpsReceiver.ps1')
        $script | Should Match 'node-v24\.18\.1-win-x64\.zip'
        $script | Should Match 'EC56B84A7551893AB2324EBDFDC4AB974A63B4781162600B68A1293CC3E53765'
        $script | Should Match 'WinSW-x64-v2\.12\.0\.exe'
        $script | Should Match '05B82D46AD331CC16BDC00DE5C6332C1EF818DF8CEEFCD49C726553209B3A0DA'
    }

    It 'prepares PostgreSQL and stable PostGIS on loopback without a database firewall rule' {
        $script = Get-Content -Raw -Encoding UTF8 (Join-Path $root 'windows\Install-FleetDatabase.ps1')
        $script | Should Match 'PostgreSQL 17\.10'
        $script | Should Match 'PostGIS 3\.6\.2'
        $script | Should Match '7BA180EE2A352987B9A2F194673652C59483B55852295CCF401DCECCD8765425'
        $script | Should Match "127\.0\.0\.1"
        $script | Should Match 'scram-sha-256'
        $script | Should Not Match 'New-NetFirewallRule[^\r\n]+5432'
    }

    It 'stops PostgreSQL while replacing PostGIS binaries and always starts it again' {
        $script = Get-Content -Raw -Encoding UTF8 (Join-Path $root 'windows\Install-FleetDatabase.ps1')
        $stop = $script.IndexOf('Stop-Service -Name $ServiceName')
        $copy = $script.IndexOf("Copy-Item -Path (Join-Path `$source.FullName '*')")
        $start = $script.IndexOf('Start-Service -Name $ServiceName', $copy)
        $stop | Should BeGreaterThan -1
        $copy | Should BeGreaterThan $stop
        $start | Should BeGreaterThan $copy
        $script | Should Match 'finally\s*\{[^}]*Start-Service -Name \$ServiceName'
    }

    It 'protects database settings with DPAPI and a restricted ProgramData ACL' {
        $script = Get-Content -Raw -Encoding UTF8 (Join-Path $root 'windows\Install-FleetDatabase.ps1')
        $script | Should Match 'ProtectedData'
        $script | Should Match 'LocalMachine'
        $script | Should Match 'NT AUTHORITY\\LOCAL SERVICE:\(OI\)\(CI\)R'
        $script | Should Match 'BUILTIN\\Administrators:\(OI\)\(CI\)F'
        $script | Should Match 'SYSTEM:\(OI\)\(CI\)F'
        $script | Should Match '\(Get-Content -Raw -LiteralPath \$Path\)\.Trim\(\)'
        $script | Should Match '@\(& \$psql[^\r\n]+rolname=''fleet_app''[^\r\n]+-join'
        $script | Should Match '@\(& \$psql[^\r\n]+datname=''fleet_tracking''[^\r\n]+-join'
    }

    It 'references a protected environment file instead of embedding a database password in XML' {
        $template = Get-Content -Raw -Encoding UTF8 (Join-Path $root 'windows\InternalGpsReceiver.xml.template')
        $template | Should Match 'GPS_ENV_FILE'
        $template | Should Not Match 'GPS_DATABASE_URL'
        $template | Should Not Match 'PASSWORD'
        $installer = Get-Content -Raw -Encoding UTF8 (Join-Path $root 'windows\Install-GpsReceiver.ps1')
        $installer | Should Match '-EnvironmentFile \$environmentFile -Migrate'
        $launcher = Get-Content -Raw -Encoding UTF8 (Join-Path $root 'windows\Start-GpsReceiver.ps1')
        $launcher | Should Match 'Add-Type -AssemblyName System\.Security'
        $launcher | Should Match '@\(\$input\)\s*-join'
        $launcher | Should Match '\$passwordInput\s*\|\s*&\s*\(Join-Path \$PSScriptRoot ''node\\node\.exe''\)'
        $launcher | Should Match 'if \(\$env:GPS_INGEST_TOKEN_SECRET_FILE\)'
        $launcher | Should Match '\$env:GPS_INGEST_TOKEN\s*=\s*Unprotect-Text \$env:GPS_INGEST_TOKEN_SECRET_FILE'
        $launcher | Should Not Match "SetEnvironmentVariable\([^\r\n]+GPS_INGEST_TOKEN[^\r\n]+(''User''|''Machine'')"
    }

    It 'performs no writes during database WhatIf' {
        $target = Join-Path $TestDrive 'fleet-data'
        $output = & (Join-Path $root 'windows\Install-FleetDatabase.ps1') -RootPath $target -WhatIf 6>&1 | Out-String
        $output | Should Match 'What if:'
        Test-Path -LiteralPath $target | Should Be $false
        $output | Should Not Match '(?i)password|secret='
    }

    It 'derives both installers from one D drive RootPath' {
        $database = Get-Content -Raw -Encoding UTF8 (Join-Path $root 'windows\Install-FleetDatabase.ps1')
        $receiver = Get-Content -Raw -Encoding UTF8 (Join-Path $root 'windows\Install-GpsReceiver.ps1')
        foreach ($script in @($database, $receiver)) {
            $script | Should Match "RootPath = 'D:\\InternalGPS'"
            $script | Should Match 'DeploymentPaths\.ps1'
        }
        $database | Should Match '\$paths\.PostgresRoot'
        $database | Should Match '\$paths\.PostgresDataRoot'
        $database | Should Match '\$paths\.ReceiverDataRoot'
        $receiver | Should Match '\$paths\.ReceiverRoot'
        $receiver | Should Match '\$paths\.ReceiverDataRoot'
        $database | Should Not Match '\[string\]\$PostgresRoot\s*='
        $receiver | Should Not Match '\[string\]\$InstallRoot\s*='
    }

    It 'performs no writes during either RootPath WhatIf' {
        $target = Join-Path $TestDrive 'InternalGPS'
        & (Join-Path $root 'windows\Install-FleetDatabase.ps1') -RootPath $target -WhatIf 6>&1 | Out-Null
        & (Join-Path $root 'windows\Install-GpsReceiver.ps1') -RootPath $target -WhatIf 6>&1 | Out-Null
        Test-Path -LiteralPath $target | Should Be $false
    }
}

Describe 'D drive deployment paths' {
    BeforeAll {
        . (Join-Path $root 'windows\DeploymentPaths.ps1')
    }

    It 'derives every project path from D:\InternalGPS' {
        $paths = Resolve-DeploymentPaths -RootPath 'D:\InternalGPS'
        $paths.Root | Should Be 'D:\InternalGPS'
        $paths.PostgresRoot | Should Be 'D:\InternalGPS\PostgreSQL'
        $paths.PostgresDataRoot | Should Be 'D:\InternalGPS\PostgreSQLData'
        $paths.ReceiverRoot | Should Be 'D:\InternalGPS\Receiver'
        $paths.ReceiverDataRoot | Should Be 'D:\InternalGPS\ReceiverData'
        $paths.BackupRoot | Should Be 'D:\InternalGPS\Backup'
    }

    It 'rejects relative, drive-root and parent-normalized paths' {
        { Resolve-DeploymentPaths -RootPath 'InternalGPS' } | Should Throw
        { Resolve-DeploymentPaths -RootPath 'D:\' } | Should Throw
        { Resolve-DeploymentPaths -RootPath 'D:\InternalGPS\..' } | Should Throw
    }

    It 'accepts only a fixed NTFS volume with at least 20 GB free' {
        Mock Get-Volume { [pscustomobject]@{ DriveType = 'Fixed'; FileSystemType = 'NTFS'; SizeRemaining = 21GB } }
        Mock Test-Path { $false }
        { Assert-DeploymentDrive (Resolve-DeploymentPaths 'D:\InternalGPS') } | Should Not Throw

        Mock Get-Volume { [pscustomobject]@{ DriveType = 'Fixed'; FileSystemType = 'ReFS'; SizeRemaining = 21GB } }
        { Assert-DeploymentDrive (Resolve-DeploymentPaths 'D:\InternalGPS') } | Should Throw

        Mock Get-Volume { [pscustomobject]@{ DriveType = 'Fixed'; FileSystemType = 'NTFS'; SizeRemaining = 19GB } }
        { Assert-DeploymentDrive (Resolve-DeploymentPaths 'D:\InternalGPS') } | Should Throw
    }

    It 'rejects an existing deployment root that is a reparse point' {
        Mock Get-Volume { [pscustomobject]@{ DriveType = 'Fixed'; FileSystemType = 'NTFS'; SizeRemaining = 21GB } }
        Mock Test-Path { $true }
        Mock Get-Item { [pscustomobject]@{ Attributes = [IO.FileAttributes]::ReparsePoint } }
        { Assert-DeploymentDrive (Resolve-DeploymentPaths 'D:\InternalGPS') } | Should Throw
    }
}

Describe 'Quick Tunnel pilot lifecycle' {
    BeforeAll {
        $pilotScriptPath = Join-Path $root 'windows\QuickTunnelPilot.ps1'
        . $pilotScriptPath
    }

    It 'accepts only an HTTPS trycloudflare.com tunnel URL' {
        $lines = @(
            'INF Quick Tunnel available at http://bad.trycloudflare.com',
            'INF Ignore https://trycloudflare.com.attacker.example',
            'INF Visit https://safe-name.trycloudflare.com to connect'
        )

        (Find-QuickTunnelUrl $lines).AbsoluteUri | Should Be 'https://safe-name.trycloudflare.com/'
        Find-QuickTunnelUrl @('INF http://bad.trycloudflare.com') | Should Be $null
        Find-QuickTunnelUrl @('INF https://trycloudflare.com.attacker.example') | Should Be $null
    }

    It 'contains the required process and secret safety controls' {
        $script = Get-Content -Raw -Encoding UTF8 $pilotScriptPath
        $script | Should Match '\[ValidateSet\(''Start'', ''Status'', ''Stop''\)\]'
        $script | Should Match 'RandomNumberGenerator'
        $script | Should Match 'DataProtectionScope\]::LocalMachine'
        $script | Should Match 'RedirectStandardError'
        $script | Should Match 'Start-Process[^\r\n]+-WindowStyle Hidden'
        $script | Should Match 'pilot-state\.json'
        $script | Should Match 'ExecutablePath'
        $script | Should Match 'GPS_INGEST_TOKEN_SECRET_FILE'
        $script | Should Not Match 'Write-(Host|Output)[^\r\n]+(token|secret)'
    }
}
