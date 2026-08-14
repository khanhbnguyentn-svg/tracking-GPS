Describe 'Release SMTP credential policy' {
    It 'allows release assembly without embedded SMTP defaults' {
        $projectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
        $gradle = Join-Path $projectRoot '.tools\gradle-8.13\bin\gradle.bat'
        $previousSigningProperties = $env:TRACKER_SIGNING_PROPERTIES
        $previousSmtpUser = $env:SMTP_USER
        $previousSmtpPassword = $env:SMTP_APP_PASSWORD
        $previousJavaHome = $env:JAVA_HOME
        $previousGradleHome = $env:GRADLE_USER_HOME
        $previousAndroidHome = $env:ANDROID_USER_HOME
        try {
            $env:TRACKER_SIGNING_PROPERTIES = Join-Path $projectRoot '.signing\signing.properties'
            $env:SMTP_USER = ''
            $env:SMTP_APP_PASSWORD = ''
            $env:JAVA_HOME = Join-Path $projectRoot '.tools\jdk-17.0.20+8'
            $env:GRADLE_USER_HOME = Join-Path $projectRoot '.tools\gradle-home'
            $env:ANDROID_USER_HOME = Join-Path $projectRoot '.tools\android-home'
            Push-Location $projectRoot
            $output = & $gradle :app:assembleRelease --offline --no-daemon 2>&1
            $exitCode = $LASTEXITCODE
        } finally {
            Pop-Location
            $env:TRACKER_SIGNING_PROPERTIES = $previousSigningProperties
            $env:SMTP_USER = $previousSmtpUser
            $env:SMTP_APP_PASSWORD = $previousSmtpPassword
            $env:JAVA_HOME = $previousJavaHome
            $env:GRADLE_USER_HOME = $previousGradleHome
            $env:ANDROID_USER_HOME = $previousAndroidHome
        }

        if ($exitCode -ne 0) {
            throw "Release build without SMTP defaults failed: $($output -join ' ')"
        }
        $buildConfig = Get-Content -Raw (Join-Path $projectRoot 'app\build\generated\source\buildConfig\release\com\internal\tracker\BuildConfig.java')
        $buildConfig | Should Match 'SMTP_USER = "";'
        $buildConfig | Should Match 'SMTP_APP_PASSWORD = "";'
    }
}

Describe 'Gradle release signing policy' {
    It 'fails release assembly when the configured signing properties file is missing' {
        $projectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
        $gradle = Join-Path $projectRoot '.tools\gradle-8.13\bin\gradle.bat'
        $previousSigningProperties = $env:TRACKER_SIGNING_PROPERTIES
        $previousSmtpUser = $env:SMTP_USER
        $previousSmtpPassword = $env:SMTP_APP_PASSWORD
        $previousJavaHome = $env:JAVA_HOME
        $previousGradleHome = $env:GRADLE_USER_HOME
        $previousAndroidHome = $env:ANDROID_USER_HOME
        try {
            $env:TRACKER_SIGNING_PROPERTIES = Join-Path $TestDrive 'missing-signing.properties'
            $env:SMTP_USER = 'sender@example.com'
            $env:SMTP_APP_PASSWORD = 'abcdefghijklmnop'
            $env:JAVA_HOME = Join-Path $projectRoot '.tools\jdk-17.0.20+8'
            $env:GRADLE_USER_HOME = Join-Path $projectRoot '.tools\gradle-home'
            $env:ANDROID_USER_HOME = Join-Path $projectRoot '.tools\android-home'
            Push-Location $projectRoot
            $output = & $gradle :app:assembleRelease --offline --no-daemon 2>&1
            $exitCode = $LASTEXITCODE
        } finally {
            Pop-Location
            $env:TRACKER_SIGNING_PROPERTIES = $previousSigningProperties
            $env:SMTP_USER = $previousSmtpUser
            $env:SMTP_APP_PASSWORD = $previousSmtpPassword
            $env:JAVA_HOME = $previousJavaHome
            $env:GRADLE_USER_HOME = $previousGradleHome
            $env:ANDROID_USER_HOME = $previousAndroidHome
        }

        $exitCode | Should Not Be 0
        ($output -join "`n") | Should Match 'Release signing properties not found'
    }

    It 'fails aggregate assembly when signing properties are missing' {
        $projectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
        $gradle = Join-Path $projectRoot '.tools\gradle-8.13\bin\gradle.bat'
        $previousSigningProperties = $env:TRACKER_SIGNING_PROPERTIES
        $previousSmtpUser = $env:SMTP_USER
        $previousSmtpPassword = $env:SMTP_APP_PASSWORD
        $previousJavaHome = $env:JAVA_HOME
        $previousGradleHome = $env:GRADLE_USER_HOME
        $previousAndroidHome = $env:ANDROID_USER_HOME
        try {
            $env:TRACKER_SIGNING_PROPERTIES = Join-Path $TestDrive 'missing-aggregate-signing.properties'
            $env:SMTP_USER = 'sender@example.com'
            $env:SMTP_APP_PASSWORD = 'abcdefghijklmnop'
            $env:JAVA_HOME = Join-Path $projectRoot '.tools\jdk-17.0.20+8'
            $env:GRADLE_USER_HOME = Join-Path $projectRoot '.tools\gradle-home'
            $env:ANDROID_USER_HOME = Join-Path $projectRoot '.tools\android-home'
            Push-Location $projectRoot
            $output = & $gradle :app:assemble --offline --no-daemon 2>&1
            $exitCode = $LASTEXITCODE
        } finally {
            Pop-Location
            $env:TRACKER_SIGNING_PROPERTIES = $previousSigningProperties
            $env:SMTP_USER = $previousSmtpUser
            $env:SMTP_APP_PASSWORD = $previousSmtpPassword
            $env:JAVA_HOME = $previousJavaHome
            $env:GRADLE_USER_HOME = $previousGradleHome
            $env:ANDROID_USER_HOME = $previousAndroidHome
        }

        $exitCode | Should Not Be 0
        ($output -join "`n") | Should Match 'Release signing properties not found'
    }

    It 'preserves JavaMail classes loaded by META-INF provider resources' {
        $projectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
        $gradle = Join-Path $projectRoot '.tools\gradle-8.13\bin\gradle.bat'
        $previousSigningProperties = $env:TRACKER_SIGNING_PROPERTIES
        $previousSmtpUser = $env:SMTP_USER
        $previousSmtpPassword = $env:SMTP_APP_PASSWORD
        $previousJavaHome = $env:JAVA_HOME
        $previousGradleHome = $env:GRADLE_USER_HOME
        $previousAndroidHome = $env:ANDROID_USER_HOME
        try {
            $env:TRACKER_SIGNING_PROPERTIES = Join-Path $projectRoot '.signing\signing.properties'
            $env:SMTP_USER = 'sender@example.com'
            $env:SMTP_APP_PASSWORD = 'abcdefghijklmnop'
            $env:JAVA_HOME = Join-Path $projectRoot '.tools\jdk-17.0.20+8'
            $env:GRADLE_USER_HOME = Join-Path $projectRoot '.tools\gradle-home'
            $env:ANDROID_USER_HOME = Join-Path $projectRoot '.tools\android-home'
            Push-Location $projectRoot
            $output = & $gradle :app:assembleRelease --offline --no-daemon 2>&1
            $exitCode = $LASTEXITCODE
        } finally {
            Pop-Location
            $env:TRACKER_SIGNING_PROPERTIES = $previousSigningProperties
            $env:SMTP_USER = $previousSmtpUser
            $env:SMTP_APP_PASSWORD = $previousSmtpPassword
            $env:JAVA_HOME = $previousJavaHome
            $env:GRADLE_USER_HOME = $previousGradleHome
            $env:ANDROID_USER_HOME = $previousAndroidHome
        }

        if ($exitCode -ne 0) {
            throw "Release build failed: $($output -join ' ')"
        }
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
