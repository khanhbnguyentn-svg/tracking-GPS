Set-StrictMode -Version Latest

function Resolve-DeploymentPaths {
    param([string]$RootPath = 'D:\InternalGPS')

    if ([string]::IsNullOrWhiteSpace($RootPath) -or -not [IO.Path]::IsPathRooted($RootPath)) {
        throw 'Deployment root must be an absolute path.'
    }
    $root = [IO.Path]::GetFullPath($RootPath).TrimEnd('\')
    $driveRoot = [IO.Path]::GetPathRoot($root).TrimEnd('\')
    if ($root -eq $driveRoot) { throw 'Deployment root cannot be a drive root.' }

    [pscustomobject]@{
        Root = $root
        PostgresRoot = Join-Path $root 'PostgreSQL'
        PostgresDataRoot = Join-Path $root 'PostgreSQLData'
        ReceiverRoot = Join-Path $root 'Receiver'
        ReceiverDataRoot = Join-Path $root 'ReceiverData'
        BackupRoot = Join-Path $root 'Backup'
    }
}

function Assert-DeploymentDrive {
    param(
        [Parameter(Mandatory = $true)][pscustomobject]$Paths,
        [int64]$MinimumFreeBytes = 20GB
    )

    $driveLetter = [IO.Path]::GetPathRoot($Paths.Root).Substring(0, 1)
    $volume = Get-Volume -DriveLetter $driveLetter -ErrorAction Stop
    if ($volume.DriveType -ne 'Fixed') { throw 'Deployment volume must be a fixed drive.' }
    if ($volume.FileSystemType -ne 'NTFS') { throw 'Deployment volume must use NTFS.' }
    if ([int64]$volume.SizeRemaining -lt $MinimumFreeBytes) { throw 'Deployment volume requires at least 20 GB free.' }

    if (Test-Path -LiteralPath $Paths.Root) {
        $item = Get-Item -LiteralPath $Paths.Root -Force
        if (($item.Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0) {
            throw 'Deployment root cannot be a reparse point.'
        }
    }
}
