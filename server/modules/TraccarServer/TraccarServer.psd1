@{
    RootModule = 'TraccarServer.psm1'
    ModuleVersion = '1.0.0'
    GUID = '90a13979-a3ee-45ed-b9d5-f655b534ee3e'
    Author = 'Internal Traccar Operations'
    Description = 'Safe Windows deployment and operations for the internal Traccar server.'
    PowerShellVersion = '5.1'
    FunctionsToExport = @(
        'Read-ServerConfig', 'Test-ServerConfig', 'New-TraccarConfig', 'New-CaddyConfig',
        'Test-ArtifactDefinition', 'Complete-VerifiedArtifact', 'Get-VerifiedArtifact',
        'Get-InstallPlan', 'Get-TraccarResourceNames'
    )
    CmdletsToExport = @()
    VariablesToExport = @()
    AliasesToExport = @()
}
