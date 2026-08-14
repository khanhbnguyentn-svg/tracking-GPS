Describe 'Verified release build command' {
    It 'builds and promotes only the approved version 2.0.1 APK' {
        $projectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
        $scriptPath = Join-Path $projectRoot 'scripts\build-release-apk.ps1'
        $outputDirectory = Join-Path $TestDrive 'dist'
        $previousSmtpUser = $env:SMTP_USER
        $previousSmtpPassword = $env:SMTP_APP_PASSWORD

        try {
            $env:SMTP_USER = 'sender@example.com'
            $env:SMTP_APP_PASSWORD = 'abcdefghijklmnop'
            $output = & powershell.exe -NoProfile -ExecutionPolicy Bypass -File $scriptPath `
                -OutputDirectory $outputDirectory 2>&1
            $exitCode = $LASTEXITCODE
        } finally {
            $env:SMTP_USER = $previousSmtpUser
            $env:SMTP_APP_PASSWORD = $previousSmtpPassword
        }

        if ($exitCode -ne 0) {
            throw "Release build command failed: $($output -join ' ')"
        }
        $releaseApk = Join-Path $outputDirectory 'tracking-gps-2.0.1.apk'
        Test-Path $releaseApk | Should Be $true
        @(Get-ChildItem $outputDirectory -File).Count | Should Be 1
        ($output -join "`n") | Should Match 'com.internal.tracker'
        ($output -join "`n") | Should Match '2.0.1 \(3\)'
        ($output -join "`n") | Should Match '8F1912A34ED2CB9DDF8840DB49A769134251B3297484333678E2C679CAE4F585'

        $mapping = Get-Content -Raw (Join-Path $projectRoot 'app\build\outputs\mapping\release\mapping.txt')
        [regex]::IsMatch(
            $mapping,
            '^com\.sun\.mail\.smtp\.SMTPSSLTransport -> com\.sun\.mail\.smtp\.SMTPSSLTransport:$',
            [System.Text.RegularExpressions.RegexOptions]::Multiline
        ) | Should Be $true
        [regex]::IsMatch(
            $mapping,
            '^com\.sun\.mail\.handlers\.text_plain -> com\.sun\.mail\.handlers\.text_plain:$',
            [System.Text.RegularExpressions.RegexOptions]::Multiline
        ) | Should Be $true
    }
}
