Describe 'Verified release build command' {
    It 'builds and promotes only the approved version 2 APK' {
        $projectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
        $scriptPath = Join-Path $projectRoot 'scripts\build-release-apk.ps1'
        $outputDirectory = Join-Path $TestDrive 'dist'

        $output = & powershell.exe -NoProfile -ExecutionPolicy Bypass -File $scriptPath `
            -OutputDirectory $outputDirectory 2>&1
        $exitCode = $LASTEXITCODE

        if ($exitCode -ne 0) {
            throw "Release build command failed: $($output -join ' ')"
        }
        $releaseApk = Join-Path $outputDirectory 'tracking-gps-2.0.0.apk'
        Test-Path $releaseApk | Should Be $true
        @(Get-ChildItem $outputDirectory -File).Count | Should Be 1
        ($output -join "`n") | Should Match 'com.internal.tracker'
        ($output -join "`n") | Should Match '2.0.0 \(2\)'
        ($output -join "`n") | Should Match '8F1912A34ED2CB9DDF8840DB49A769134251B3297484333678E2C679CAE4F585'
    }
}
