$modulePath = Join-Path $PSScriptRoot '..\modules\TraccarServer\TraccarServer.psd1'
Import-Module $modulePath -Force

Describe 'Server configuration templates' {
    BeforeEach {
        $script:config = @{
            LanAddress = '192.168.1.10'; LanPrefixLength = 24; AdminRemoteAddress = '192.168.1.0/24'
            PublicGpsPort = 5055; PublicWebPort = 8082; TraccarGpsPort = 15055
            TraccarWebPort = 18082; PostgresPort = 5432
            InstallRoot = 'C:\Program Files\InternalTraccar'; DataRoot = 'C:\ProgramData\InternalTraccar'
            BackupRoot = 'C:\ProgramData\InternalTraccar\backups'; BackupRetentionDays = 30; DiskWarningPercent = 85
        }
    }

    It 'renders valid Traccar XML with escaped credentials and strict device registration' {
        $xmlText = New-TraccarConfig -Config $script:config -DatabaseUser 'traccar' -DatabasePassword 'a&b<c' -PendingGroupId 12
        { [xml]$xmlText } | Should Not Throw
        $xmlText | Should Match 'jdbc:postgresql://127\.0\.0\.1:5432/traccar'
        $xmlText | Should Match 'a&amp;b&lt;c'
        $xmlText | Should Match '\^AND-\[0-9A-F\]\{16\}\$'
        $xmlText | Should Match '<entry key=''database\.registerUnknown\.defaultGroupId''>12</entry>'
        $xmlText | Should Not Match '@@TOKEN@@'
    }

    It 'rejects a non-positive pending group ID' {
        { New-TraccarConfig -Config $script:config -DatabaseUser 'traccar' -DatabasePassword 'secret' -PendingGroupId 0 } |
            Should Throw 'PendingGroupId must be a positive integer.'
    }

    It 'renders Caddy TLS endpoints backed only by loopback ports' {
        $text = New-CaddyConfig -Config $script:config -ServerName 'tracker.lan'
        $text | Should Match 'tracker\.lan:5055'
        $text | Should Match 'tracker\.lan:8082'
        $text | Should Match 'reverse_proxy 127\.0\.0\.1:15055'
        $text | Should Match 'reverse_proxy 127\.0\.0\.1:18082'
        $text | Should Match 'tls internal'
        $text | Should Not Match ':5432'
        $text | Should Not Match '@@TOKEN@@'
    }

    It 'rejects an invalid server name' {
        { New-CaddyConfig -Config $script:config -ServerName 'https://tracker.lan/path' } |
            Should Throw 'ServerName must be a hostname or IPv4 address without scheme, port, or path.'
    }
}
