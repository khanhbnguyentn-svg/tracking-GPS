Set-StrictMode -Version Latest

function Invoke-NativeTool {
    param(
        [Parameter(Mandatory)][string]$FilePath,
        [Parameter(Mandatory)][string[]]$Arguments
    )

    $previousErrorActionPreference = $ErrorActionPreference
    try {
        $ErrorActionPreference = 'Continue'
        $output = & $FilePath @Arguments 2>&1
        $exitCode = $LASTEXITCODE
    } finally {
        $ErrorActionPreference = $previousErrorActionPreference
    }
    return [PSCustomObject]@{ ExitCode = $exitCode; Output = @($output) }
}

function Normalize-Fingerprint {
    [CmdletBinding()]
    param([Parameter(Mandatory)][string]$Fingerprint)

    $normalized = ($Fingerprint -replace '[:\s]', '').ToUpperInvariant()
    if ($normalized.Length -eq 0 -or $normalized.Length % 2 -ne 0 -or $normalized -notmatch '^[0-9A-F]+$') {
        throw 'Fingerprint must contain an even number of hexadecimal characters.'
    }
    return $normalized
}

function Get-UnbiasedRandomIndex {
    param(
        [Parameter(Mandatory)]$Generator,
        [Parameter(Mandatory)][ValidateRange(1, 255)][int]$UpperBound
    )

    $buffer = New-Object byte[] 1
    $limit = [Math]::Floor(256 / $UpperBound) * $UpperBound
    do {
        $Generator.GetBytes($buffer)
    } while ($buffer[0] -ge $limit)
    return $buffer[0] % $UpperBound
}

function New-ReleasePassword {
    [CmdletBinding()]
    param([ValidateRange(3, 256)][int]$Length = 32)

    $upper = 'ABCDEFGHIJKLMNOPQRSTUVWXYZ'
    $lower = 'abcdefghijklmnopqrstuvwxyz'
    $digits = '0123456789'
    $alphabet = $upper + $lower + $digits
    $characters = New-Object char[] $Length
    $generator = [System.Security.Cryptography.RandomNumberGenerator]::Create()
    try {
        $randomIndex = Get-UnbiasedRandomIndex -Generator $generator -UpperBound $upper.Length
        $characters[0] = $upper[$randomIndex]
        $randomIndex = Get-UnbiasedRandomIndex -Generator $generator -UpperBound $lower.Length
        $characters[1] = $lower[$randomIndex]
        $randomIndex = Get-UnbiasedRandomIndex -Generator $generator -UpperBound $digits.Length
        $characters[2] = $digits[$randomIndex]
        for ($index = 3; $index -lt $Length; $index++) {
            $randomIndex = Get-UnbiasedRandomIndex -Generator $generator -UpperBound $alphabet.Length
            $characters[$index] = $alphabet[$randomIndex]
        }
    } finally {
        $generator.Dispose()
    }
    return -join $characters
}

function Get-KeyStoreFingerprint {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)][string]$KeyToolPath,
        [Parameter(Mandatory)][string]$KeyStorePath,
        [Parameter(Mandatory)][string]$StorePassword,
        [Parameter(Mandatory)][string]$Alias
    )

    if (-not (Test-Path -LiteralPath $KeyToolPath -PathType Leaf)) {
        throw "keytool was not found: $KeyToolPath"
    }
    if (-not (Test-Path -LiteralPath $KeyStorePath -PathType Leaf)) {
        throw "Keystore was not found: $KeyStorePath"
    }

    $certificatePath = Join-Path (Split-Path -Parent $KeyStorePath) `
        ('.release-cert-{0}.der' -f [Guid]::NewGuid().ToString('N'))
    try {
        $exportResult = Invoke-NativeTool -FilePath $KeyToolPath -Arguments @(
            '-exportcert', '-alias', $Alias, '-keystore', $KeyStorePath,
            '-storepass', $StorePassword, '-file', $certificatePath
        )
        if ($exportResult.ExitCode -ne 0 -or -not (Test-Path -LiteralPath $certificatePath -PathType Leaf)) {
            throw "Could not export certificate for alias '$Alias': $($exportResult.Output -join ' ')"
        }

        $certificate = New-Object System.Security.Cryptography.X509Certificates.X509Certificate2($certificatePath)
        $sha256 = [System.Security.Cryptography.SHA256]::Create()
        try {
            $hash = $sha256.ComputeHash($certificate.RawData)
        } finally {
            $sha256.Dispose()
            $certificate.Dispose()
        }
        return ([BitConverter]::ToString($hash) -replace '-', '')
    } finally {
        Remove-Item -LiteralPath $certificatePath -Force -ErrorAction SilentlyContinue
    }
}

function Initialize-ReleaseSigning {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)][string]$KeyToolPath,
        [Parameter(Mandatory)][string]$SourceKeyStore,
        [Parameter(Mandatory)][string]$SourceStorePassword,
        [Parameter(Mandatory)][string]$SourceAlias,
        [Parameter(Mandatory)][string]$DestinationDirectory,
        [Parameter(Mandatory)][string]$ExpectedFingerprint,
        [string]$StoreFileProperty = '.signing/tracker-release.p12'
    )

    $expected = Normalize-Fingerprint $ExpectedFingerprint
    $sourceFingerprint = Get-KeyStoreFingerprint -KeyToolPath $KeyToolPath `
        -KeyStorePath $SourceKeyStore -StorePassword $SourceStorePassword -Alias $SourceAlias
    if ($sourceFingerprint -ne $expected) {
        throw "Source signing certificate fingerprint does not match the approved fingerprint. Expected $expected, found $sourceFingerprint."
    }

    $destinationKeyStore = Join-Path $DestinationDirectory 'tracker-release.p12'
    $propertiesPath = Join-Path $DestinationDirectory 'signing.properties'
    if (Test-Path -LiteralPath $destinationKeyStore) {
        throw "Destination keystore already exists: $destinationKeyStore"
    }
    if (Test-Path -LiteralPath $propertiesPath) {
        throw "Destination signing properties already exists: $propertiesPath"
    }

    $createdDirectory = $false
    $createdKeyStore = $false
    $createdProperties = $false
    try {
        if (-not (Test-Path -LiteralPath $DestinationDirectory -PathType Container)) {
            New-Item -ItemType Directory -Path $DestinationDirectory | Out-Null
            $createdDirectory = $true
        }

        $password = New-ReleasePassword -Length 32
        $importResult = Invoke-NativeTool -FilePath $KeyToolPath -Arguments @(
            '-importkeystore', '-noprompt', '-srckeystore', $SourceKeyStore,
            '-srcstorepass', $SourceStorePassword, '-srcalias', $SourceAlias,
            '-destkeystore', $destinationKeyStore, '-deststoretype', 'PKCS12',
            '-deststorepass', $password, '-destkeypass', $password,
            '-destalias', 'tracker-release'
        )
        if ($importResult.ExitCode -ne 0 -or -not (Test-Path -LiteralPath $destinationKeyStore -PathType Leaf)) {
            throw "Could not import the approved release signing key: $($importResult.Output -join ' ')"
        }
        $createdKeyStore = $true

        $lines = @(
            "storeFile=$StoreFileProperty",
            "storePassword=$password",
            'keyAlias=tracker-release',
            "keyPassword=$password"
        )
        $utf8WithoutBom = New-Object System.Text.UTF8Encoding($false)
        [IO.File]::WriteAllLines($propertiesPath, $lines, $utf8WithoutBom)
        $createdProperties = $true

        $destinationFingerprint = Get-KeyStoreFingerprint -KeyToolPath $KeyToolPath `
            -KeyStorePath $destinationKeyStore -StorePassword $password -Alias 'tracker-release'
        if ($destinationFingerprint -ne $expected) {
            throw "Imported release signing certificate fingerprint does not match the approved fingerprint."
        }

        return [PSCustomObject]@{
            KeyStorePath = $destinationKeyStore
            PropertiesPath = $propertiesPath
            Alias = 'tracker-release'
            Fingerprint = $destinationFingerprint
        }
    } catch {
        if ($createdProperties) {
            Remove-Item -LiteralPath $propertiesPath -Force -ErrorAction SilentlyContinue
        }
        if ($createdKeyStore) {
            Remove-Item -LiteralPath $destinationKeyStore -Force -ErrorAction SilentlyContinue
        }
        if ($createdDirectory -and (Test-Path -LiteralPath $DestinationDirectory -PathType Container)) {
            Remove-Item -LiteralPath $DestinationDirectory -ErrorAction SilentlyContinue
        }
        throw
    }
}

function Get-ApkIdentity {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)][string]$AaptPath,
        [Parameter(Mandatory)][string]$ApkSignerPath,
        [Parameter(Mandatory)][string]$ApkPath
    )

    foreach ($toolPath in @($AaptPath, $ApkSignerPath, $ApkPath)) {
        if (-not (Test-Path -LiteralPath $toolPath -PathType Leaf)) {
            throw "Required release file was not found: $toolPath"
        }
    }

    $badgingResult = Invoke-NativeTool -FilePath $AaptPath -Arguments @('dump', 'badging', $ApkPath)
    if ($badgingResult.ExitCode -ne 0) {
        throw "Could not inspect APK package metadata: $($badgingResult.Output -join ' ')"
    }
    $packageLine = $badgingResult.Output | Where-Object { $_ -match '^package:' } | Select-Object -First 1
    if (-not $packageLine -or $packageLine -notmatch "name='([^']+)'\s+versionCode='([^']+)'\s+versionName='([^']+)'") {
        throw 'APK package metadata did not contain package name, versionCode, and versionName.'
    }
    $packageName = $Matches[1]
    $versionCode = $Matches[2]
    $versionName = $Matches[3]

    $signingResult = Invoke-NativeTool -FilePath $ApkSignerPath -Arguments @('verify', '--print-certs', $ApkPath)
    if ($signingResult.ExitCode -ne 0) {
        throw "APK signature verification failed: $($signingResult.Output -join ' ')"
    }
    $fingerprintLine = $signingResult.Output |
        Where-Object { $_ -match 'certificate SHA-256 digest:\s*([0-9a-fA-F:]+)' } |
        Select-Object -First 1
    if (-not $fingerprintLine -or $fingerprintLine -notmatch 'certificate SHA-256 digest:\s*([0-9a-fA-F:]+)') {
        throw 'APK signature output did not contain a SHA-256 certificate fingerprint.'
    }

    return [PSCustomObject]@{
        PackageName = $packageName
        VersionCode = $versionCode
        VersionName = $versionName
        Fingerprint = Normalize-Fingerprint $Matches[1]
    }
}

function Assert-ApkIdentity {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)][string]$AaptPath,
        [Parameter(Mandatory)][string]$ApkSignerPath,
        [Parameter(Mandatory)][string]$ApkPath,
        [Parameter(Mandatory)][string]$ExpectedPackage,
        [Parameter(Mandatory)][string]$ExpectedVersionCode,
        [Parameter(Mandatory)][string]$ExpectedVersionName,
        [Parameter(Mandatory)][string]$ExpectedFingerprint
    )

    $identity = Get-ApkIdentity -AaptPath $AaptPath -ApkSignerPath $ApkSignerPath -ApkPath $ApkPath
    if ($identity.PackageName -ne $ExpectedPackage) {
        throw "APK package mismatch. Expected $ExpectedPackage, found $($identity.PackageName)."
    }
    if ($identity.VersionCode -ne $ExpectedVersionCode) {
        throw "APK versionCode mismatch. Expected $ExpectedVersionCode, found $($identity.VersionCode)."
    }
    if ($identity.VersionName -ne $ExpectedVersionName) {
        throw "APK versionName mismatch. Expected $ExpectedVersionName, found $($identity.VersionName)."
    }
    $expectedNormalizedFingerprint = Normalize-Fingerprint $ExpectedFingerprint
    if ($identity.Fingerprint -ne $expectedNormalizedFingerprint) {
        throw "APK signing certificate fingerprint mismatch. Expected $expectedNormalizedFingerprint, found $($identity.Fingerprint)."
    }
    return $identity
}

function Publish-VerifiedApk {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)][string]$AaptPath,
        [Parameter(Mandatory)][string]$ApkSignerPath,
        [Parameter(Mandatory)][string]$SourceApkPath,
        [Parameter(Mandatory)][string]$OutputDirectory,
        [Parameter(Mandatory)][string]$ExpectedPackage,
        [Parameter(Mandatory)][string]$ExpectedVersionCode,
        [Parameter(Mandatory)][string]$ExpectedVersionName,
        [Parameter(Mandatory)][string]$ExpectedFingerprint
    )

    $identity = Assert-ApkIdentity -AaptPath $AaptPath -ApkSignerPath $ApkSignerPath `
        -ApkPath $SourceApkPath -ExpectedPackage $ExpectedPackage `
        -ExpectedVersionCode $ExpectedVersionCode -ExpectedVersionName $ExpectedVersionName `
        -ExpectedFingerprint $ExpectedFingerprint

    $destinationPath = Join-Path $OutputDirectory ("tracking-gps-{0}.apk" -f $identity.VersionName)
    $partialPath = "$destinationPath.partial"
    if (Test-Path -LiteralPath $destinationPath) {
        throw "Release APK already exists for version $($identity.VersionName): $destinationPath"
    }
    if (-not (Test-Path -LiteralPath $OutputDirectory -PathType Container)) {
        New-Item -ItemType Directory -Path $OutputDirectory | Out-Null
    }
    try {
        Remove-Item -LiteralPath $partialPath -Force -ErrorAction SilentlyContinue
        Copy-Item -LiteralPath $SourceApkPath -Destination $partialPath
        Move-Item -LiteralPath $partialPath -Destination $destinationPath
    } finally {
        Remove-Item -LiteralPath $partialPath -Force -ErrorAction SilentlyContinue
    }

    return [PSCustomObject]@{
        ApkPath = $destinationPath
        PackageName = $identity.PackageName
        VersionCode = $identity.VersionCode
        VersionName = $identity.VersionName
        Fingerprint = $identity.Fingerprint
    }
}

Export-ModuleMember -Function Normalize-Fingerprint, New-ReleasePassword, `
    Get-KeyStoreFingerprint, Initialize-ReleaseSigning, Get-ApkIdentity, Assert-ApkIdentity, `
    Publish-VerifiedApk
