param(
    [string]$BaseUrl = "http://localhost:8080",

    [ValidateRange(1, 100000)]
    [int]$JobCount = 1000,

    [ValidateRange(1, 100)]
    [int]$Concurrency = 20,

    [ValidateRange(10, 3600)]
    [int]$PerJobTimeoutSeconds = 120
)

$ErrorActionPreference = "Stop"

if ($PSVersionTable.PSVersion.Major -lt 7) {
    throw "This load test requires PowerShell 7 or newer"
}

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
    throw "ChronosQ is not ready. Status: $($health.status)"
}

Write-Host "ChronosQ is ready" -ForegroundColor Green


Write-Step "Submitting $JobCount jobs with concurrency $Concurrency"

$runId = [Guid]::NewGuid().ToString("N")
$completeTestWatch = [System.Diagnostics.Stopwatch]::StartNew()
$submissionWatch = [System.Diagnostics.Stopwatch]::StartNew()

$submissionResults = 1..$JobCount |
    ForEach-Object -Parallel {

        $jobNumber = $_

        $requestBody = @{
            queueName      = "default"
            jobType        = "PRINT_MESSAGE"
            payload        = @{
                message = "Load-test job $jobNumber"
            }
            priority       = 0
            scheduleType   = "IMMEDIATE"
            maxAttempts    = 3
            idempotencyKey = "load-test-$using:runId-$jobNumber"
            timeoutSeconds = 30
        } | ConvertTo-Json -Depth 5

        $requestWatch =
            [System.Diagnostics.Stopwatch]::StartNew()

        try {
            $response = Invoke-WebRequest `
                -Method Post `
                -Uri "$using:BaseUrl/api/v1/jobs" `
                -ContentType "application/json" `
                -Body $requestBody

            $requestWatch.Stop()

            $responseBody =
                $response.Content |
                ConvertFrom-Json

            $jobId = $responseBody.id

            if ($null -eq $jobId) {
                $jobId = $responseBody.jobId
            }

            [PSCustomObject]@{
                Number     = $jobNumber
                Success    = $response.StatusCode -in @(200, 201, 202)
                JobId      = $jobId
                DurationMs = $requestWatch.Elapsed.TotalMilliseconds
                Error      = $null
            }
        }
        catch {
            $requestWatch.Stop()

            [PSCustomObject]@{
                Number     = $jobNumber
                Success    = $false
                JobId      = $null
                DurationMs = $requestWatch.Elapsed.TotalMilliseconds
                Error      = $_.Exception.Message
            }
        }

    } -ThrottleLimit $Concurrency

$submissionWatch.Stop()

$successfulSubmissions = @(
    $submissionResults |
        Where-Object {
            $_.Success -and
            -not [string]::IsNullOrWhiteSpace($_.JobId)
        }
)

$failedSubmissions = @(
    $submissionResults |
        Where-Object {
            -not $_.Success -or
            [string]::IsNullOrWhiteSpace($_.JobId)
        }
)

if ($successfulSubmissions.Count -eq 0) {
    throw "Every job submission failed"
}


Write-Step "Waiting for submitted jobs to finish"

$completionResults = $successfulSubmissions |
    ForEach-Object -Parallel {

        $submittedJob = $_
        $deadline =
            (Get-Date).AddSeconds(
                $using:PerJobTimeoutSeconds
            )

        $terminalStatuses = @(
            "SUCCEEDED",
            "FAILED",
            "DEAD_LETTERED",
            "CANCELLED"
        )

        $lastStatus = "UNKNOWN"
        $lastError = $null

        do {
            try {
                $job = Invoke-RestMethod `
                    -Method Get `
                    -Uri "$using:BaseUrl/api/v1/jobs/$($submittedJob.JobId)"

                $lastStatus = $job.status
                $lastError = $null

                if ($terminalStatuses -contains $lastStatus) {
                    break
                }
            }
            catch {
                $lastError = $_.Exception.Message
            }

            Start-Sleep -Milliseconds 500

        } while ((Get-Date) -lt $deadline)

        [PSCustomObject]@{
            JobId  = $submittedJob.JobId
            Status = $lastStatus
            TimedOut =
                -not ($terminalStatuses -contains $lastStatus)
            Error  = $lastError
        }

    } -ThrottleLimit $Concurrency

$completeTestWatch.Stop()


Write-Step "Calculating load-test results"

$durations = @(
    $submissionResults.DurationMs |
        Sort-Object
)

$averageSubmissionMs =
    ($durations |
        Measure-Object -Average
    ).Average

$p95Index = [Math]::Max(
    0,
    [Math]::Ceiling($durations.Count * 0.95) - 1
)

$p95SubmissionMs = $durations[$p95Index]

$submissionSeconds =
    [Math]::Max(
        $submissionWatch.Elapsed.TotalSeconds,
        0.001
    )

$submissionThroughput =
    $successfulSubmissions.Count /
    $submissionSeconds

$failureRate =
    ($failedSubmissions.Count / $JobCount) * 100

$succeededJobs = @(
    $completionResults |
        Where-Object Status -eq "SUCCEEDED"
).Count

$failedJobs = @(
    $completionResults |
        Where-Object {
            $_.Status -in @(
                "FAILED",
                "DEAD_LETTERED",
                "CANCELLED"
            )
        }
).Count

$timedOutJobs = @(
    $completionResults |
        Where-Object TimedOut
).Count

$summary = [PSCustomObject]@{
    RunId                    = $runId
    RequestedJobs            = $JobCount
    AcceptedJobs             = $successfulSubmissions.Count
    SubmissionFailures       = $failedSubmissions.Count
    SubmissionFailureRatePct = [Math]::Round($failureRate, 2)
    SubmissionThroughput     = [Math]::Round($submissionThroughput, 2)
    AverageSubmissionMs      = [Math]::Round($averageSubmissionMs, 2)
    P95SubmissionMs          = [Math]::Round($p95SubmissionMs, 2)
    SucceededJobs            = $succeededJobs
    FailedTerminalJobs       = $failedJobs
    TimedOutWaitingJobs      = $timedOutJobs
    SubmissionTimeSeconds    =
        [Math]::Round(
            $submissionWatch.Elapsed.TotalSeconds,
            2
        )
    QueueDrainTimeSeconds    =
        [Math]::Round(
            $completeTestWatch.Elapsed.TotalSeconds,
            2
        )
}

Write-Host ""
$summary | Format-List


Write-Step "Saving the result"

$outputDirectory =
    Join-Path $PSScriptRoot "..\target"

New-Item `
    -ItemType Directory `
    -Path $outputDirectory `
    -Force |
    Out-Null

$outputFile =
    Join-Path $outputDirectory "load-test-summary.csv"

$summary |
    Export-Csv `
        -Path $outputFile `
        -NoTypeInformation

Write-Host "Result saved to: $outputFile" `
    -ForegroundColor Green

if ($failedSubmissions.Count -gt 0) {
    Write-Warning "$($failedSubmissions.Count) submissions failed"
}

if ($timedOutJobs -gt 0) {
    Write-Warning "$timedOutJobs jobs did not finish before the timeout"
}

if ($succeededJobs -eq $successfulSubmissions.Count) {
    Write-Host ""
    Write-Host "ChronosQ load test passed!" `
        -ForegroundColor Green
}