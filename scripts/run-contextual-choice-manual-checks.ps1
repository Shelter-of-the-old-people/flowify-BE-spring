param(
    [string[]]$Scenarios = @(
        "drive-family",
        "github-family",
        "gmail-family",
        "sheets-family",
        "news-notion-v2",
        "seboard-notion-v2"
    )
)

$ErrorActionPreference = "Stop"

if ($Scenarios.Count -eq 1 -and $Scenarios[0] -is [string] -and $Scenarios[0].Contains(",")) {
    $Scenarios = $Scenarios[0].Split(",") | ForEach-Object { $_.Trim() } | Where-Object { $_ }
}

$repoRoot = Split-Path -Parent $PSScriptRoot
$summaryPath = Join-Path $repoRoot ".tmp-manual-contextual-choice-summary.json"
$runnerLogDir = Join-Path $repoRoot ".tmp-manual-runner"

$scenarioMap = @{
    "drive-family" = @{
        Script = ".tmp-manual-ui-drive-family.mjs"
        OutputDir = ".tmp-manual-ui-drive-family"
        ResultFile = "summary.json"
        Description = "Google Drive source -> summary/table middle options -> sink restriction"
    }
    "github-family" = @{
        Script = ".tmp-manual-ui-github-family.mjs"
        OutputDir = ".tmp-manual-ui-github-family"
        ResultFile = "summary.json"
        Description = "GitHub source -> AI/filter middle options -> sink restriction"
    }
    "gmail-family" = @{
        Script = ".tmp-manual-ui-gmail-family.mjs"
        OutputDir = ".tmp-manual-ui-gmail-family"
        ResultFile = "summary.json"
        Description = "Gmail source -> single email middle options -> text/sheets sink restriction"
    }
    "sheets-family" = @{
        Script = ".tmp-manual-ui-sheets-family.mjs"
        OutputDir = ".tmp-manual-ui-sheets-family"
        ResultFile = "result.json"
        Description = "Google Sheets source -> spreadsheet middle options -> text sink restriction"
    }
    "calendar-family" = @{
        Script = ".tmp-manual-ui-calendar-family.mjs"
        OutputDir = ".tmp-manual-ui-calendar-family"
        ResultFile = "result.json"
        Description = "Google Calendar source -> schedule middle options -> calendar sink visibility"
    }
    "article-sheets-sink" = @{
        Script = ".tmp-manual-ui-article-sheets-sink.mjs"
        OutputDir = ".tmp-manual-ui-news-sheets"
        ResultFile = "result.json"
        Description = "Naver News source -> text refine -> Google Sheets sink"
    }
    "article-text-sink" = @{
        Script = ".tmp-manual-ui-article-text-sink.mjs"
        OutputDir = ".tmp-manual-ui-seboard-notion"
        ResultFile = "result.json"
        Description = "SE Board source -> text refine -> Notion sink"
    }
    "canvas-notion" = @{
        Script = ".tmp-manual-ui-canvas-notion.mjs"
        OutputDir = ".tmp-manual-ui-canvas-notion"
        ResultFile = "result.json"
        Description = "Canvas LMS source -> Notion sink legacy flow"
    }
    "canvas-notion-v2" = @{
        Script = ".tmp-manual-ui-canvas-notion-v2.mjs"
        OutputDir = ".tmp-manual-ui-canvas-notion-v2"
        ResultFile = "result.json"
        Description = "Canvas LMS source -> text refine -> Notion sink"
    }
    "github-gmail" = @{
        Script = ".tmp-manual-ui-github-gmail.mjs"
        OutputDir = ".tmp-manual-ui-github-gmail"
        ResultFile = "result.json"
        Description = "GitHub source -> Gmail sink legacy flow"
    }
    "gmail-notion" = @{
        Script = ".tmp-manual-ui-gmail-notion.mjs"
        OutputDir = ".tmp-manual-ui-gmail-notion"
        ResultFile = "result.json"
        Description = "Gmail source -> Notion sink legacy flow"
    }
    "gmail-todo-notion-v2" = @{
        Script = ".tmp-manual-ui-gmail-todo-notion-v2.mjs"
        OutputDir = ".tmp-manual-ui-gmail-todo-notion-v2"
        ResultFile = "result.json"
        Description = "Gmail source -> todo extraction -> Notion sink"
    }
    "news-notion-v2" = @{
        Script = ".tmp-manual-ui-news-notion-v2.mjs"
        OutputDir = ".tmp-manual-ui-news-notion-v2"
        ResultFile = "result.json"
        Description = "Naver News source -> text refine middle option -> Notion sink"
    }
    "seboard-notion" = @{
        Script = ".tmp-manual-ui-seboard-notion.mjs"
        OutputDir = ".tmp-manual-ui-seboard-notion"
        ResultFile = "result.json"
        Description = "SE Board source -> Notion sink legacy flow"
    }
    "seboard-notion-v2" = @{
        Script = ".tmp-manual-ui-seboard-notion-v2.mjs"
        OutputDir = ".tmp-manual-ui-seboard-notion-v2"
        ResultFile = "result.json"
        Description = "SE Board source -> loop/refine middle option -> Notion sink"
    }
}

function Test-Endpoint {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Url,
        [switch]$AllowUnauthorized
    )

    try {
        $response = Invoke-WebRequest -UseBasicParsing $Url -TimeoutSec 10
        return [pscustomobject]@{
            url = $Url
            ok = $true
            status = [int]$response.StatusCode
            message = $null
        }
    } catch {
        $statusCode = $null
        if ($_.Exception.Response -and $_.Exception.Response.StatusCode) {
            $statusCode = [int]$_.Exception.Response.StatusCode
        }
        if ($AllowUnauthorized -and $statusCode -eq 401) {
            return [pscustomobject]@{
                url = $Url
                ok = $true
                status = 401
                message = "unauthorized but reachable"
            }
        }
        return [pscustomobject]@{
            url = $Url
            ok = $false
            status = $statusCode
            message = $_.Exception.Message
        }
    }
}

function Read-JsonFile {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Path
    )

    if (-not (Test-Path $Path)) {
        return $null
    }

    return Get-Content $Path -Raw -Encoding UTF8 | ConvertFrom-Json
}

$preflight = @(
    (Test-Endpoint -Url "http://localhost:5173"),
    (Test-Endpoint -Url "http://localhost:8000/api/v1/health"),
    (Test-Endpoint -Url "http://localhost:8080/api/v1/health" -AllowUnauthorized)
)

$failedPreflight = $preflight | Where-Object { -not $_.ok }
if ($failedPreflight.Count -gt 0) {
    $summary = [pscustomobject]@{
        ok = $false
        startedAt = (Get-Date).ToString("o")
        finishedAt = (Get-Date).ToString("o")
        preflight = $preflight
        results = @()
    }
    $summary | ConvertTo-Json -Depth 8 | Set-Content $summaryPath -Encoding UTF8
    throw "Manual validation preflight failed. See $summaryPath"
}

$results = @()
$startedAt = Get-Date

New-Item -ItemType Directory -Path $runnerLogDir -Force | Out-Null

foreach ($scenarioName in $Scenarios) {
    if (-not $scenarioMap.ContainsKey($scenarioName)) {
        throw "Unknown scenario: $scenarioName"
    }

    $scenario = $scenarioMap[$scenarioName]
    $scriptPath = Join-Path $repoRoot $scenario.Script
    $outputDir = Join-Path $repoRoot $scenario.OutputDir
    $resultPath = Join-Path $outputDir $scenario.ResultFile
    $scenarioStartedAt = Get-Date
    $stdoutPath = Join-Path $runnerLogDir "$scenarioName-stdout.log"
    $stderrPath = Join-Path $runnerLogDir "$scenarioName-stderr.log"
    $previousArtifactWriteTimeUtc = $null

    if (Test-Path $stdoutPath) {
        Remove-Item $stdoutPath -Force
    }
    if (Test-Path $stderrPath) {
        Remove-Item $stderrPath -Force
    }
    if (Test-Path $resultPath) {
        $previousArtifactWriteTimeUtc = (Get-Item $resultPath).LastWriteTimeUtc
    }

    $process = Start-Process `
        -FilePath "node" `
        -ArgumentList $scriptPath `
        -WorkingDirectory $repoRoot `
        -NoNewWindow `
        -Wait `
        -PassThru `
        -RedirectStandardOutput $stdoutPath `
        -RedirectStandardError $stderrPath

    $exitCode = $process.ExitCode
    $stdout = [string]$(if (Test-Path $stdoutPath) { Get-Content $stdoutPath -Raw -Encoding UTF8 } else { "" })
    $stderr = [string]$(if (Test-Path $stderrPath) { Get-Content $stderrPath -Raw -Encoding UTF8 } else { "" })
    $artifactFresh = $false

    if (Test-Path $resultPath) {
        $currentArtifactWriteTimeUtc = (Get-Item $resultPath).LastWriteTimeUtc
        $artifactFresh = ($previousArtifactWriteTimeUtc -eq $null) -or ($currentArtifactWriteTimeUtc -gt $previousArtifactWriteTimeUtc)
    }

    $artifact = if ($artifactFresh) { Read-JsonFile -Path $resultPath } else { $null }
    $artifactSuccess = $null
    $stdoutText = if ($null -ne $stdout) { $stdout.Trim() } else { "" }
    $stderrText = if ($null -ne $stderr) { $stderr.Trim() } else { "" }
    if ($artifact -and $artifact.PSObject.Properties.Name -contains "results") {
        $artifactSuccess = (($artifact.results | Where-Object { $_.ok -eq $false }).Count -eq 0)
    } elseif ($artifact -and $artifact.PSObject.Properties.Name -contains "ok") {
        $artifactSuccess = [bool]$artifact.ok
    }

    $results += [pscustomobject]@{
        scenario = $scenarioName
        description = $scenario.Description
        script = $scenario.Script
        outputDir = $scenario.OutputDir
        resultFile = $scenario.ResultFile
        ok = ($exitCode -eq 0) -and ($artifactSuccess -ne $false)
        exitCode = $exitCode
        startedAt = $scenarioStartedAt.ToString("o")
        finishedAt = (Get-Date).ToString("o")
        stdout = $stdoutText
        stderr = $stderrText
        artifactFresh = $artifactFresh
        artifactSuccess = $artifactSuccess
        artifact = $artifact
    }
}

$summary = [pscustomobject]@{
    ok = ($results | Where-Object { -not $_.ok }).Count -eq 0
    startedAt = $startedAt.ToString("o")
    finishedAt = (Get-Date).ToString("o")
    preflight = $preflight
    results = $results
}

$summary | ConvertTo-Json -Depth 10 | Set-Content $summaryPath -Encoding UTF8
$summary | ConvertTo-Json -Depth 10
