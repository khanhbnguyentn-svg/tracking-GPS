$root = Split-Path $PSScriptRoot -Parent

Describe 'Windows service assets' {
    It 'defines a stable service with automatic restart and port 5055' {
        $template = Get-Content -Raw -Encoding UTF8 (Join-Path $root 'windows\InternalGpsReceiver.xml.template')
        $template | Should Match '<id>InternalGpsReceiver</id>'
        $template | Should Match '<startmode>Automatic</startmode>'
        $template | Should Match '<onfailure action="restart"'
        $template | Should Match 'GPS_PORT" value="5055"'
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
}
