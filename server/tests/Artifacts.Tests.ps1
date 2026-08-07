$modulePath = Join-Path $PSScriptRoot '..\modules\TraccarServer\TraccarServer.psd1'
Import-Module $modulePath -Force

Describe 'Artifact safety' {
    It 'rejects non-HTTPS artifact URLs' {
        { Test-ArtifactDefinition -Definition @{ Url='http://example.test/a.zip'; Sha256=('a' * 64); FileName='a.zip' } } |
            Should Throw 'Artifact URL must use HTTPS.'
    }

    It 'rejects malformed SHA-256 values' {
        { Test-ArtifactDefinition -Definition @{ Url='https://example.test/a.zip'; Sha256='abc'; FileName='a.zip' } } |
            Should Throw 'Artifact Sha256 must contain exactly 64 hexadecimal characters.'
    }

    It 'rejects artifact filenames that escape the cache' {
        { Test-ArtifactDefinition -Definition @{ Url='https://example.test/a.zip'; Sha256=('a' * 64); FileName='..\a.zip' } } |
            Should Throw 'Artifact FileName must be a plain file name.'
    }

    It 'detects a hash mismatch without promoting the partial file' {
        $root = Join-Path $TestDrive 'cache'
        New-Item -ItemType Directory -Path $root | Out-Null
        $partial = Join-Path $root 'a.zip.partial'
        [IO.File]::WriteAllText($partial, 'wrong')
        { Complete-VerifiedArtifact -PartialPath $partial -FinalPath (Join-Path $root 'a.zip') -ExpectedSha256 ('0' * 64) } |
            Should Throw 'Downloaded artifact SHA-256 does not match the manifest.'
        Test-Path (Join-Path $root 'a.zip') | Should Be $false
    }
}
