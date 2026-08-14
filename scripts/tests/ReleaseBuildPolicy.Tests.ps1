Describe 'Gradle release signing policy' {
    It 'fails release assembly before signing when the SMTP user is missing' {
        $projectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
        $gradle = Join-Path $projectRoot '.tools\gradle-8.13\bin\gradle.bat'
        $previous = @{
            Signing = $env:TRACKER_SIGNING_PROPERTIES
            SmtpUser = $env:SMTP_USER
            SmtpPassword = $env:SMTP_APP_PASSWORD
            JavaHome = $env:JAVA_HOME
            GradleHome = $env:GRADLE_USER_HOME
            AndroidHome = $env:ANDROID_USER_HOME
        }
        try {
            $env:TRACKER_SIGNING_PROPERTIES = Join-Path $TestDrive 'missing-signing.properties'
            $env:SMTP_USER = ''
            $env:SMTP_APP_PASSWORD = 'abcdefghijklmnop'
            $env:JAVA_HOME = Join-Path $projectRoot '.tools\jdk-17.0.20+8'
            $env:GRADLE_USER_HOME = Join-Path $projectRoot '.tools\gradle-home'
            $env:ANDROID_USER_HOME = Join-Path $projectRoot '.tools\android-home'
            Push-Location $projectRoot
            $output = & $gradle :app:assembleRelease --offline --no-daemon 2>&1
            $exitCode = $LASTEXITCODE
        } finally {
            Pop-Location
            $env:TRACKER_SIGNING_PROPERTIES = $previous.Signing
            $env:SMTP_USER = $previous.SmtpUser
            $env:SMTP_APP_PASSWORD = $previous.SmtpPassword
            $env:JAVA_HOME = $previous.JavaHome
            $env:GRADLE_USER_HOME = $previous.GradleHome
            $env:ANDROID_USER_HOME = $previous.AndroidHome
        }

        $exitCode | Should Not Be 0
        ($output -join "`n") | Should Match 'Release SMTP user is missing or invalid'
        ($output -join "`n") | Should Not Match 'abcdefghijklmnop'
    }

    It 'fails release assembly before signing when the SMTP App Password is invalid' {
        $projectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
        $gradle = Join-Path $projectRoot '.tools\gradle-8.13\bin\gradle.bat'
        $previous = @{
            Signing = $env:TRACKER_SIGNING_PROPERTIES
            SmtpUser = $env:SMTP_USER
            SmtpPassword = $env:SMTP_APP_PASSWORD
            JavaHome = $env:JAVA_HOME
            GradleHome = $env:GRADLE_USER_HOME
            AndroidHome = $env:ANDROID_USER_HOME
        }
        try {
            $env:TRACKER_SIGNING_PROPERTIES = Join-Path $TestDrive 'missing-signing.properties'
            $env:SMTP_USER = 'sender@example.com'
            $env:SMTP_APP_PASSWORD = 'short'
            $env:JAVA_HOME = Join-Path $projectRoot '.tools\jdk-17.0.20+8'
            $env:GRADLE_USER_HOME = Join-Path $projectRoot '.tools\gradle-home'
            $env:ANDROID_USER_HOME = Join-Path $projectRoot '.tools\android-home'
            Push-Location $projectRoot
            $output = & $gradle :app:assembleRelease --offline --no-daemon 2>&1
            $exitCode = $LASTEXITCODE
        } finally {
            Pop-Location
            $env:TRACKER_SIGNING_PROPERTIES = $previous.Signing
            $env:SMTP_USER = $previous.SmtpUser
            $env:SMTP_APP_PASSWORD = $previous.SmtpPassword
            $env:JAVA_HOME = $previous.JavaHome
            $env:GRADLE_USER_HOME = $previous.GradleHome
            $env:ANDROID_USER_HOME = $previous.AndroidHome
        }

        $exitCode | Should Not Be 0
        ($output -join "`n") | Should Match 'Release SMTP App Password must contain exactly 16 non-whitespace characters'
        ($output -join "`n") | Should Not Match 'short'
    }

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
}
