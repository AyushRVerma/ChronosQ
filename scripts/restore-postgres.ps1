param(
    [Parameter(Mandatory = $true)]
    [string]$BackupPath,

    [string]$ComposeFile = "compose.prod.yml",

    [string]$DatabaseUser = "chronosq",

    [string]$TargetDatabase = "chronosq_restore_test",

    [switch]$ReplaceExistingDatabase,

    [switch]$ConfirmRestore
)

$ErrorActionPreference = "Stop"

$containerBackupPath =
    "/tmp/chronosq-restore.dump"

function Write-Step {
    param([string]$Message)

    Write-Host ""
    Write-Host "==> $Message" -ForegroundColor Cyan
}


if (-not $ConfirmRestore) {
    throw @"
Restore confirmation was not provided.

Run the command again with:

    -ConfirmRestore

Restoration can replace database data, so explicit confirmation is required.
"@
}

if (-not (Test-Path -LiteralPath $ComposeFile)) {
    throw "Compose file was not found: $ComposeFile"
}

if (-not (Test-Path -LiteralPath $BackupPath)) {
    throw "Backup file was not found: $BackupPath"
}

if ($TargetDatabase -notmatch "^[a-zA-Z_][a-zA-Z0-9_]*$") {
    throw @"
Invalid target database name.

Use only letters, numbers and underscores.
The first character must be a letter or underscore.
"@
}

$resolvedBackupPath =
    (Resolve-Path -LiteralPath $BackupPath).Path

$backupFile =
    Get-Item -LiteralPath $resolvedBackupPath

if ($backupFile.Length -le 0) {
    throw "The backup file is empty"
}


Write-Step "Checking backup checksum"

$checksumPath =
    "$resolvedBackupPath.sha256"

if (Test-Path -LiteralPath $checksumPath) {
    $expectedChecksum =
        (
            Get-Content `
                -LiteralPath $checksumPath `
                -Raw
        ).Trim().Split(
            [char[]]" `t",
            [System.StringSplitOptions]::RemoveEmptyEntries
        )[0]

    $actualChecksum =
        (
            Get-FileHash `
                -LiteralPath $resolvedBackupPath `
                -Algorithm SHA256
        ).Hash

    if (
        $expectedChecksum.ToUpperInvariant() -ne
        $actualChecksum.ToUpperInvariant()
    ) {
        throw @"
Backup checksum verification failed.

Expected: $expectedChecksum
Actual:   $actualChecksum
"@
    }

    Write-Host "Backup checksum is valid" `
        -ForegroundColor Green
}
else {
    Write-Warning @"
No checksum file was found at:

$checksumPath

The backup cannot be checked for corruption.
"@
}


Write-Step "Checking PostgreSQL"

docker compose `
    -f $ComposeFile `
    exec `
    -T `
    postgres `
    pg_isready `
    -U $DatabaseUser `
    -d postgres

if ($LASTEXITCODE -ne 0) {
    throw "PostgreSQL is not ready"
}


Write-Step "Checking the target database"

$databaseExistsQuery = @"
SELECT 1
FROM pg_database
WHERE datname = '$TargetDatabase';
"@

$databaseExistsResult =
    (
        docker compose `
            -f $ComposeFile `
            exec `
            -T `
            postgres `
            psql `
            --username=$DatabaseUser `
            --dbname=postgres `
            --tuples-only `
            --no-align `
            --command=$databaseExistsQuery |
        Out-String
    ).Trim()

$databaseExists =
    $databaseExistsResult -eq "1"

if ($databaseExists -and -not $ReplaceExistingDatabase) {
    throw @"
The target database '$TargetDatabase' already exists.

Choose a different database name or explicitly provide:

    -ReplaceExistingDatabase
"@
}


if ($databaseExists) {
    Write-Step "Disconnecting clients from the existing target database"

    $terminateConnectionsQuery = @"
SELECT pg_terminate_backend(pid)
FROM pg_stat_activity
WHERE datname = '$TargetDatabase'
  AND pid <> pg_backend_pid();
"@

    docker compose `
        -f $ComposeFile `
        exec `
        -T `
        postgres `
        psql `
        --username=$DatabaseUser `
        --dbname=postgres `
        --command=$terminateConnectionsQuery

    if ($LASTEXITCODE -ne 0) {
        throw "Could not disconnect clients from $TargetDatabase"
    }


    Write-Step "Removing the existing target database"

    docker compose `
        -f $ComposeFile `
        exec `
        -T `
        postgres `
        dropdb `
        --username=$DatabaseUser `
        --if-exists `
        $TargetDatabase

    if ($LASTEXITCODE -ne 0) {
        throw "Could not remove the existing target database"
    }
}


Write-Step "Creating the target database"

docker compose `
    -f $ComposeFile `
    exec `
    -T `
    postgres `
    createdb `
    --username=$DatabaseUser `
    $TargetDatabase

if ($LASTEXITCODE -ne 0) {
    throw "Could not create target database: $TargetDatabase"
}


try {
    Write-Step "Copying the backup into the PostgreSQL container"

    docker compose `
        -f $ComposeFile `
        cp `
        $resolvedBackupPath `
        "postgres:$containerBackupPath"

    if ($LASTEXITCODE -ne 0) {
        throw "Could not copy the backup into the container"
    }


    Write-Step "Restoring the backup"

    docker compose `
        -f $ComposeFile `
        exec `
        -T `
        postgres `
        pg_restore `
        --username=$DatabaseUser `
        --dbname=$TargetDatabase `
        --format=custom `
        --no-owner `
        --no-privileges `
        --exit-on-error `
        $containerBackupPath

    if ($LASTEXITCODE -ne 0) {
        throw "pg_restore failed with exit code $LASTEXITCODE"
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


Write-Step "Verifying restored database tables"

$tableCountQuery = @"
SELECT COUNT(*)
FROM information_schema.tables
WHERE table_schema = 'public';
"@

$tableCountResult =
    (
        docker compose `
            -f $ComposeFile `
            exec `
            -T `
            postgres `
            psql `
            --username=$DatabaseUser `
            --dbname=$TargetDatabase `
            --tuples-only `
            --no-align `
            --command=$tableCountQuery |
        Out-String
    ).Trim()

$tableCount = 0

if (-not [int]::TryParse(
    $tableCountResult,
    [ref]$tableCount
)) {
    throw "Could not determine the restored table count"
}

if ($tableCount -le 0) {
    throw "Restore finished, but the database contains no public tables"
}


Write-Host ""
Write-Host "ChronosQ restore completed successfully!" `
    -ForegroundColor Green

Write-Host "Target database: $TargetDatabase"
Write-Host "Restored tables: $tableCount"
Write-Host ""
Write-Host @"
The application still points to its normal database.
This restored database should now be inspected and tested before it is used.
"@