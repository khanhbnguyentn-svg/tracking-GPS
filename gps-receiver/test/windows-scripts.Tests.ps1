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

    It 'limits the firewall rule to Private LocalSubnet' {
        $script = Get-Content -Raw -Encoding UTF8 (Join-Path $root 'windows\Install-GpsReceiver.ps1')
        $script | Should Match "Profile 'Private'"
        $script | Should Match "RemoteAddress 'LocalSubnet'"
        $script | Should Not Match 'Profile Any'
    }

    It 'uses project-owned stable resource names' {
        $script = Get-Content -Raw -Encoding UTF8 (Join-Path $root 'windows\Install-GpsReceiver.ps1')
        $script | Should Match "ServiceName = 'InternalGpsReceiver'"
        $script | Should Match "FirewallName = 'InternalGpsReceiver-5055'"
    }

    It 'preserves ProgramData unless PurgeData is explicitly supplied' {
        $script = Get-Content -Raw -Encoding UTF8 (Join-Path $root 'windows\Uninstall-GpsReceiver.ps1')
        $script | Should Match '\[switch\]\$PurgeData'
        $script | Should Match 'if \(\$PurgeData\)'
        $script | Should Not Match 'Remove-Item[^\r\n]+DataRoot[^\r\n]+$'
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

    It 'protects database settings with DPAPI and a restricted ProgramData ACL' {
        $script = Get-Content -Raw -Encoding UTF8 (Join-Path $root 'windows\Install-FleetDatabase.ps1')
        $script | Should Match 'ProtectedData'
        $script | Should Match 'LocalMachine'
        $script | Should Match 'NT AUTHORITY\\LOCAL SERVICE:\(OI\)\(CI\)R'
        $script | Should Match 'BUILTIN\\Administrators:\(OI\)\(CI\)F'
        $script | Should Match 'SYSTEM:\(OI\)\(CI\)F'
    }

    It 'references a protected environment file instead of embedding a database password in XML' {
        $template = Get-Content -Raw -Encoding UTF8 (Join-Path $root 'windows\InternalGpsReceiver.xml.template')
        $template | Should Match 'GPS_ENV_FILE'
        $template | Should Not Match 'GPS_DATABASE_URL'
        $template | Should Not Match 'PASSWORD'
    }

    It 'performs no writes during database WhatIf' {
        $target = Join-Path $TestDrive 'fleet-data'
        $output = & (Join-Path $root 'windows\Install-FleetDatabase.ps1') -DataRoot $target -WhatIf 6>&1 | Out-String
        $output | Should Match 'What if:'
        Test-Path -LiteralPath $target | Should Be $false
        $output | Should Not Match '(?i)password|secret='
    }
}
