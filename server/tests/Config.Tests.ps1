$modulePath = Join-Path $PSScriptRoot '..\modules\TraccarServer\TraccarServer.psd1'
Import-Module $modulePath -Force

Describe 'Server configuration validation' {
    BeforeEach {
        $script:validConfig = @{
            LanAddress = '192.168.1.10'
            LanPrefixLength = 24
            AdminRemoteAddress = '192.168.1.0/24'
            PublicGpsPort = 5055
            PublicWebPort = 8082
            TraccarGpsPort = 15055
            TraccarWebPort = 18082
            PostgresPort = 5432
            InstallRoot = 'C:\Program Files\InternalTraccar'
            DataRoot = 'C:\ProgramData\InternalTraccar'
            BackupRoot = 'C:\ProgramData\InternalTraccar\backups'
            BackupRetentionDays = 30
            DiskWarningPercent = 85
        }
    }

    It 'accepts the documented configuration' {
        { Test-ServerConfig -Config $script:validConfig } | Should Not Throw
    }

    It 'rejects an invalid LAN address' {
        $script:validConfig.LanAddress = '999.1.1.1'
        { Test-ServerConfig -Config $script:validConfig } | Should Throw 'LanAddress must be a valid IPv4 address.'
    }

    It 'rejects duplicate ports' {
        $script:validConfig.PublicWebPort = 5055
        { Test-ServerConfig -Config $script:validConfig } | Should Throw 'All configured ports must be unique.'
    }

    It 'rejects ports outside the valid range' {
        $script:validConfig.PostgresPort = 70000
        { Test-ServerConfig -Config $script:validConfig } | Should Throw 'PostgresPort must be between 1 and 65535.'
    }

    It 'rejects a root directory as a managed path' {
        $script:validConfig.DataRoot = 'C:\'
        { Test-ServerConfig -Config $script:validConfig } | Should Throw 'DataRoot must not be a drive root.'
    }

    It 'requires at least seven days of backup retention' {
        $script:validConfig.BackupRetentionDays = 6
        { Test-ServerConfig -Config $script:validConfig } | Should Throw 'BackupRetentionDays must be between 7 and 365.'
    }

    It 'restricts the disk warning threshold' {
        $script:validConfig.DiskWarningPercent = 49
        { Test-ServerConfig -Config $script:validConfig } | Should Throw 'DiskWarningPercent must be between 50 and 95.'
    }

    It 'rejects unknown configuration keys' {
        $script:validConfig.Unexpected = 'value'
        { Test-ServerConfig -Config $script:validConfig } | Should Throw 'Unknown configuration key: Unexpected'
    }
}
