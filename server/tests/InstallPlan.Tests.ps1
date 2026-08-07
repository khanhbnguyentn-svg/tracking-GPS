$modulePath = Join-Path $PSScriptRoot '..\modules\TraccarServer\TraccarServer.psd1'
Import-Module $modulePath -Force

Describe 'Installation plan' {
    It 'returns the safe deterministic component order' {
        $plan = Get-InstallPlan
        ($plan -join ',') | Should Be 'preflight,postgres,traccar,caddy,firewall,tasks,smoke'
    }

    It 'uses stable project-owned Windows resource names' {
        $names = Get-TraccarResourceNames
        $names.CaddyService | Should Be 'InternalTraccar-Caddy'
        $names.GpsFirewall | Should Be 'InternalTraccar-GPS-HTTPS'
        $names.WebFirewall | Should Be 'InternalTraccar-Web-HTTPS'
        $names.BackupTask | Should Be 'InternalTraccar-Backup'
    }
}
