$modulePath = Join-Path $PSScriptRoot '..\release\ReleaseTools.psm1'
Import-Module $modulePath -Force -DisableNameChecking

Describe 'Release identity primitives' {
    It 'normalizes a SHA-256 fingerprint without changing its bytes' {
        Normalize-Fingerprint 'aa:BB:01' | Should Be 'AABB01'
    }

    It 'creates a non-default 32-character release password' {
        $password = New-ReleasePassword -Length 32

        $password.Length | Should Be 32
        $password | Should Not Be 'android'
        $password | Should Match '[A-Z]'
        $password | Should Match '[a-z]'
        $password | Should Match '[0-9]'
    }
}

Describe 'Release keystore preparation' {
    BeforeAll {
        $projectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
        $keyTool = Join-Path $projectRoot '.tools\jdk-17.0.20+8\bin\keytool.exe'
        $sourceKeyStore = Join-Path $TestDrive 'source.p12'
        & $keyTool -genkeypair -alias source -keyalg RSA -keysize 2048 -validity 365 `
            -dname 'CN=Release Test' -storetype PKCS12 -keystore $sourceKeyStore `
            -storepass 'SourcePass123' -keypass 'SourcePass123' 2>&1 | Out-Null
        if ($LASTEXITCODE -ne 0) {
            throw 'Could not create the disposable source keystore.'
        }
    }

    It 'reads the real SHA-256 certificate fingerprint from a keystore' {
        $fingerprint = Get-KeyStoreFingerprint -KeyToolPath $keyTool `
            -KeyStorePath $sourceKeyStore -StorePassword 'SourcePass123' -Alias 'source'

        $fingerprint | Should Match '^[0-9A-F]{64}$'
    }

    It 'rejects a source signer with the wrong approved fingerprint' {
        $message = $null
        try {
            Initialize-ReleaseSigning -KeyToolPath $keyTool -SourceKeyStore $sourceKeyStore `
                -SourceStorePassword 'SourcePass123' -SourceAlias 'source' `
                -DestinationDirectory (Join-Path $TestDrive 'wrong-signing') `
                -ExpectedFingerprint ('0' * 64)
        } catch {
            $message = $_.Exception.Message
        }

        $message | Should Match 'fingerprint'
    }

    It 'imports the same certificate and writes a usable private signing config' {
        $fingerprint = Get-KeyStoreFingerprint -KeyToolPath $keyTool `
            -KeyStorePath $sourceKeyStore -StorePassword 'SourcePass123' -Alias 'source'

        $result = Initialize-ReleaseSigning -KeyToolPath $keyTool -SourceKeyStore $sourceKeyStore `
            -SourceStorePassword 'SourcePass123' -SourceAlias 'source' `
            -DestinationDirectory (Join-Path $TestDrive 'signing') `
            -ExpectedFingerprint $fingerprint

        $result.Fingerprint | Should Be $fingerprint
        Test-Path $result.KeyStorePath | Should Be $true
        Test-Path $result.PropertiesPath | Should Be $true
        (Get-Content -Raw $result.PropertiesPath) | Should Match 'keyAlias=tracker-release'
    }

    It 'never overwrites an existing destination keystore' {
        $fingerprint = Get-KeyStoreFingerprint -KeyToolPath $keyTool `
            -KeyStorePath $sourceKeyStore -StorePassword 'SourcePass123' -Alias 'source'
        $destination = Join-Path $TestDrive 'existing'
        New-Item -ItemType Directory -Path $destination | Out-Null
        $existingKeyStore = Join-Path $destination 'tracker-release.p12'
        [IO.File]::WriteAllText($existingKeyStore, 'keep')

        $message = $null
        try {
            Initialize-ReleaseSigning -KeyToolPath $keyTool -SourceKeyStore $sourceKeyStore `
                -SourceStorePassword 'SourcePass123' -SourceAlias 'source' `
                -DestinationDirectory $destination -ExpectedFingerprint $fingerprint
        } catch {
            $message = $_.Exception.Message
        }

        $message | Should Match 'already exists'
        (Get-Content -Raw $existingKeyStore) | Should Be 'keep'
    }
}

Describe 'APK release identity' {
    It 'reads and enforces package version and signing certificate from a real APK' {
        if ([string]::IsNullOrWhiteSpace($env:TEST_RELEASE_APK)) {
            Set-TestInconclusive 'TEST_RELEASE_APK is not set.'
            return
        }

        $projectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
        $buildTools = Get-ChildItem (Join-Path $projectRoot '.tools\android-sdk\build-tools') -Directory |
            Sort-Object Name -Descending | Select-Object -First 1
        $aaptPath = Join-Path $buildTools.FullName 'aapt.exe'
        $apkSignerPath = Join-Path $buildTools.FullName 'apksigner.bat'

        $identity = Get-ApkIdentity -AaptPath $aaptPath -ApkSignerPath $apkSignerPath `
            -ApkPath $env:TEST_RELEASE_APK

        $identity.PackageName | Should Be $env:TEST_EXPECTED_PACKAGE
        $identity.VersionCode | Should Be $env:TEST_EXPECTED_VERSION_CODE
        $identity.VersionName | Should Be $env:TEST_EXPECTED_VERSION_NAME
        $identity.Fingerprint | Should Be (Normalize-Fingerprint $env:TEST_EXPECTED_FINGERPRINT)

        { Assert-ApkIdentity -AaptPath $aaptPath -ApkSignerPath $apkSignerPath `
            -ApkPath $env:TEST_RELEASE_APK -ExpectedPackage $env:TEST_EXPECTED_PACKAGE `
            -ExpectedVersionCode $env:TEST_EXPECTED_VERSION_CODE `
            -ExpectedVersionName $env:TEST_EXPECTED_VERSION_NAME `
            -ExpectedFingerprint $env:TEST_EXPECTED_FINGERPRINT } | Should Not Throw
    }
}
