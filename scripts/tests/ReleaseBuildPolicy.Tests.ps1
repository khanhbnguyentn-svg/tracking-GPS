Describe 'Gradle release signing policy' {
    It 'fails release assembly when the configured signing properties file is missing' {
        $projectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
        $gradle = Join-Path $projectRoot '.tools\gradle-8.13\bin\gradle.bat'
        $previousSigningProperties = $env:TRACKER_SIGNING_PROPERTIES
        $previousJavaHome = $env:JAVA_HOME
        $previousGradleHome = $env:GRADLE_USER_HOME
        $previousAndroidHome = $env:ANDROID_USER_HOME
        try {
            $env:TRACKER_SIGNING_PROPERTIES = Join-Path $TestDrive 'missing-signing.properties'
            $env:JAVA_HOME = Join-Path $projectRoot '.tools\jdk-17.0.20+8'
            $env:GRADLE_USER_HOME = Join-Path $projectRoot '.tools\gradle-home'
            $env:ANDROID_USER_HOME = Join-Path $projectRoot '.tools\android-home'
            Push-Location $projectRoot
            $output = & $gradle :app:assembleRelease --offline --no-daemon 2>&1
            $exitCode = $LASTEXITCODE
        } finally {
            Pop-Location
            $env:TRACKER_SIGNING_PROPERTIES = $previousSigningProperties
            $env:JAVA_HOME = $previousJavaHome
            $env:GRADLE_USER_HOME = $previousGradleHome
            $env:ANDROID_USER_HOME = $previousAndroidHome
        }

        $exitCode | Should Not Be 0
        ($output -join "`n") | Should Match 'Release signing properties not found'
    }
}
