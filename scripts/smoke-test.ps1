param(
    [string]$BaseUrl = "http://localhost:8080",
    [int]$TimeoutSeconds = 60
)

$ErrorActionPreference = "Stop"

function Write-Step {
    param([string]$Message)

    Write-Host ""
    Write-Host "==> $Message" -ForegroundColor Cyan
}

Write-Step "Checking ChronosQ readiness"

$health = Invoke-RestMethod `
    -Method Get `
    -Uri "$BaseUrl/actuator/health/readiness"

if ($health.status -ne "UP") {
    throw "ChronosQ is not ready. Current status: $($health.status)"
}

Write-Host "ChronosQ readiness status: UP" -ForegroundColor Green


Write-Step "Submitting an immediate PRINT_MESSAGE job"

$idempotencyKey = "smoke-test-$([Guid]::NewGuid())"

$requestBody = @{
    queueName       = "default"
    jobType         = "PRINT_MESSAGE"
    payload         = @{
        message = "ChronosQ production smoke test"
    }
    priority        = 0
    scheduleType    = "IMMEDIATE"
    maxAttempts     = 3
    idempotencyKey  = $idempotencyKey
    timeoutSeconds  = 30
} | ConvertTo-Json -Depth 5

$submittedJob = Invoke-RestMethod `
    -Method Post `
    -Uri "$BaseUrl/api/v1/jobs" `
    -ContentType "application/json" `
    -Body $requestBody

$jobId = $submittedJob.id

if ($null -eq $jobId) {
    $jobId = $submittedJob.jobId
}

if ([string]::IsNullOrWhiteSpace($jobId)) {
    throw "Submission succeeded, but the response did not contain id or jobId"
}

Write-Host "Submitted job: $jobId" -ForegroundColor Green


Write-Step "Waiting for the job to reach a terminal status"

$deadline = (Get-Date).AddSeconds($TimeoutSeconds)

$terminalStatuses = @(
    "SUCCEEDED",
    "FAILED",
    "DEAD_LETTERED",
    "CANCELLED"
)

do {
    $job = Invoke-RestMethod `
        -Method Get `
        -Uri "$BaseUrl/api/v1/jobs/$jobId"

    Write-Host "Current job status: $($job.status)"

    if ($terminalStatuses -contains $job.status) {
        break
    }

    Start-Sleep -Seconds 1

} while ((Get-Date) -lt $deadline)

if ($job.status -ne "SUCCEEDED") {
    throw "Smoke-test job did not succeed. Final status: $($job.status)"
}

Write-Host "Job completed successfully" -ForegroundColor Green


Write-Step "Checking execution history"

$executions = Invoke-RestMethod `
    -Method Get `
    -Uri "$BaseUrl/api/v1/jobs/$jobId/executions"

if ($null -eq $executions) {
    throw "Execution-history endpoint returned no response"
}

Write-Host "Execution history is accessible" -ForegroundColor Green


Write-Step "Checking Prometheus metrics"

$metrics = Invoke-WebRequest `
    -Method Get `
    -Uri "$BaseUrl/actuator/prometheus"

if ($metrics.Content -notmatch "chronosq_") {
    throw "Prometheus endpoint does not contain ChronosQ metrics"
}

Write-Host "ChronosQ metrics are accessible" -ForegroundColor Green


Write-Host ""
Write-Host "ChronosQ smoke test passed successfully!" `
    -ForegroundColor Green