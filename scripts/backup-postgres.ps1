param(
    [string]$ComposeFile = "compose.prod.yml",

    [string]$DatabaseName = "chronosq",

    [string]$DatabaseUser = "chronosq",

    [string]$OutputDirectory = (
        Join-Path `
            ([Environment]::GetFolderPath("MyDocuments")) `
            "ChronosQ-Backups"
    )
)

$ErrorActionPreference = "Stop"

$containerBackupPath =
    "/tmp/chronosq-backup.dump"

function Write-Step {
    param([string]$Message)

    Write-Host ""
    Write-Host "==> $Message" -ForegroundColor Cyan
}

if (-not (Test-Path -LiteralPath $ComposeFile)) {
    throw "Compose file was not found: $ComposeFile"
}

New-Item `
    -ItemType Directory `
    -Path $OutputDirectory `
    -Force |
    Out-Null

$timestamp =
    Get-Date -Format "yyyyMMdd-HHmmss"

$backupFileName =
    "chronosq-$timestamp.dump"

$localBackupPath =
    Join-Path `
        $OutputDirectory `
        $backupFileName


Write-Step "Checking the PostgreSQL container"

docker compose `
    -f $ComposeFile `
    exec `
    -T `
    postgres `
    pg_isready `
    -U $DatabaseUser `
    -d $DatabaseName

if ($LASTEXITCODE -ne 0) {
    throw "PostgreSQL is not ready"
}


try {
    Write-Step "Creating a PostgreSQL backup"

    docker compose `
        -f $ComposeFile `
        exec `
        -T `
        postgres `
        pg_dump `
        --username=$DatabaseUser `
        --dbname=$DatabaseName `
        --format=custom `
        --compress=6 `
        --no-owner `
        --no-privileges `
        --file=$containerBackupPath

    if ($LASTEXITCODE -ne 0) {
        throw "pg_dump failed with exit code $LASTEXITCODE"
    }


    Write-Step "Copying the backup from the container"

    docker compose `
        -f $ComposeFile `
        cp `
        "postgres:$containerBackupPath" `
        $localBackupPath

    if ($LASTEXITCODE -ne 0) {
        throw "Docker could not copy the backup file"
    }
}
finally {
    Write-Step "Removing the temporary container backup"

    docker compose `
        -f $ComposeFile `
        exec `
        -T `
        postgres `
        rm `
        -f `
        $containerBackupPath |
        Out-Null
}


if (-not (Test-Path -LiteralPath $localBackupPath)) {
    throw "The local backup file was not created"
}

$backupFile =
    Get-Item -LiteralPath $localBackupPath

if ($backupFile.Length -le 0) {
    throw "The backup file is empty"
}


Write-Step "Calculating the SHA-256 checksum"

$checksum =
    Get-FileHash `
        -LiteralPath $localBackupPath `
        -Algorithm SHA256

$checksumPath =
    "$localBackupPath.sha256"

"$($checksum.Hash)  $backupFileName" |
    Set-Content `
        -LiteralPath $checksumPath `
        -Encoding UTF8


Write-Host ""
Write-Host "ChronosQ backup completed successfully!" `
    -ForegroundColor Green

Write-Host "Backup:  $localBackupPath"
Write-Host "Size:    $($backupFile.Length) bytes"
Write-Host "SHA-256: $($checksum.Hash)"
Write-Host "Checksum file: $checksumPath"