# Allows this script to behave like a proper PowerShell command
# with named parameters such as -Alias and -OutputPath.
[CmdletBinding()]
param(
    # Default location where the generated keystore will be stored.
    #
    # $PSScriptRoot represents the directory containing this script.
    # If this script is inside "scripts", ".." moves to the project root.
    [string]$OutputPath = (
        Join-Path `
            $PSScriptRoot `
            "..\secrets\chronosq-jwt.p12"
    ),

    # A keystore can contain multiple keys.
    # The alias is the name used to find our JWT signing key.
    [string]$Alias = "chronosq-jwt",

    # Number of days for which the generated certificate remains valid.
    # The value must be between one and ten years.
    [ValidateRange(365, 3650)]
    [int]$ValidityDays = 3650
)


# Makes PowerShell report errors when we use:
# - an undefined variable
# - a missing property
# - other unsafe scripting behaviour
Set-StrictMode -Version Latest


# Converts normally non-terminating PowerShell errors
# into terminating errors so that the script stops immediately.
$ErrorActionPreference = "Stop"


# Read the keystore password from an environment variable.
#
# We do not write the real password inside this script because
# this file may eventually be committed to GitHub.
$password =
        $env:CHRONOSQ_JWT_KEYSTORE_PASSWORD


# Stop when the environment variable has not been provided.
if ([string]::IsNullOrWhiteSpace($password)) {
    throw @'
CHRONOSQ_JWT_KEYSTORE_PASSWORD is missing.

Set it before running this script:

$env:CHRONOSQ_JWT_KEYSTORE_PASSWORD =
        "replace-with-a-strong-password"
'@
}


# Reject passwords that are obviously too short.
#
# This is only a minimum validation rule.
# A production password should be long and randomly generated.
if ($password.Length -lt 12) {
    throw @'
CHRONOSQ_JWT_KEYSTORE_PASSWORD must contain
at least 12 characters.
'@
}


# Allow only safe characters in the key alias.
#
# Valid examples:
# chronosq-jwt
# chronosq.jwt
# jwt_key_01
if ($Alias -notmatch "^[A-Za-z0-9._-]{1,100}$") {
    throw "The JWT key alias contains invalid characters."
}


# Find keytool.exe from the installed JDK.
#
# keytool is Java's command-line utility for creating:
# - cryptographic keys
# - certificates
# - keystores
$keytoolCommand =
        Get-Command `
            "keytool.exe" `
            -ErrorAction Stop


# Convert the output path into a complete absolute path.
#
# Example:
# ..\secrets\chronosq-jwt.p12
#
# becomes:
# D:\SPRINGBOOT\ChronosQ\chronosq\secrets\chronosq-jwt.p12
$absoluteOutputPath =
        [System.IO.Path]::GetFullPath(
                $OutputPath
        )


# Extract only the parent directory.
#
# For:
# D:\project\secrets\chronosq-jwt.p12
#
# the parent directory is:
# D:\project\secrets
$outputDirectory =
        Split-Path `
            -Parent `
            $absoluteOutputPath


# Create the secrets directory if it does not already exist.
if (-not (Test-Path -LiteralPath $outputDirectory)) {
    New-Item `
        -ItemType Directory `
        -Path $outputDirectory `
        | Out-Null
}


# Never overwrite an existing signing key automatically.
#
# Replacing the key would cause tokens signed with the previous
# private key to fail verification.
if (Test-Path -LiteralPath $absoluteOutputPath) {
    throw @"
A JWT keystore already exists:

$absoluteOutputPath

It was not overwritten because replacing it would
invalidate JWTs signed with the old private key.
"@
}


# Prepare the arguments that will be sent to keytool.
#
# Using an argument array avoids fragile PowerShell line
# continuation characters in the actual keytool command.
$keytoolArguments = @(
    # Generate an asymmetric public/private key pair.
    "-genkeypair"

    # Name under which the key will be stored.
    "-alias"
    $Alias

    # Use the RSA asymmetric encryption algorithm.
    "-keyalg"
    "RSA"

    # Generate a strong 3072-bit RSA key.
    "-keysize"
    "3072"

    # Use SHA-256 together with RSA for the certificate signature.
    "-sigalg"
    "SHA256withRSA"

    # Certificate validity in days.
    "-validity"
    $ValidityDays.ToString()

    # Identity information stored inside the certificate.
    "-dname"
    "CN=ChronosQ JWT Signing, OU=Security, O=ChronosQ, C=IN"

    # Use the standard PKCS12 keystore format.
    "-storetype"
    "PKCS12"

    # Location where keytool should create the keystore.
    "-keystore"
    $absoluteOutputPath

    # Tell keytool to read the password from the named
    # environment variable instead of the command line.
    "-storepass:env"
    "CHRONOSQ_JWT_KEYSTORE_PASSWORD"

    # Do not ask interactive questions.
    "-noprompt"
)


# Execute keytool using the arguments prepared above.
& $keytoolCommand.Source @keytoolArguments


# Native programs return an exit code.
# Zero means success; anything else means failure.
if ($LASTEXITCODE -ne 0) {
    throw "keytool failed with exit code $LASTEXITCODE."
}


# Display a success message after the keystore has been created.
Write-Host ""
Write-Host "JWT signing keystore created successfully."
Write-Host "Location:  $absoluteOutputPath"
Write-Host "Alias:     $Alias"
Write-Host "Algorithm: RSA 3072-bit"