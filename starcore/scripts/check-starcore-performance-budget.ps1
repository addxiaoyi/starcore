[CmdletBinding()]
param(
    [string]$ProjectRoot = '',
    [string]$BudgetConfigPath = '',
    [string]$SmokeSummaryJsonPath = '',
    [string]$BaselineSummaryPath = '',
    [int]$BaselineWindowSize = 0,
    [string]$BaselineAggregation = '',
    [string]$OutputDir = '',
    [switch]$AllowMissingSmoke
)

$ErrorActionPreference = 'Stop'

. (Join-Path $PSScriptRoot 'starcore-runtime-common.ps1')

function Get-LatestFilePath {
    param(
        [string]$BaseDirectory,
        [string]$DirectoryPattern,
        [string]$FileName
    )
    $latest = Get-ChildItem -LiteralPath $BaseDirectory -Directory -ErrorAction SilentlyContinue |
        Where-Object { $_.Name -like $DirectoryPattern } |
        Sort-Object Name -Descending |
        ForEach-Object {
            $candidate = Join-Path $_.FullName $FileName
            if (Test-Path -LiteralPath $candidate) {
                return $candidate
            }
        } |
        Select-Object -First 1

    if (-not $latest) {
        return ''
    }
    return (Resolve-Path -LiteralPath $latest).Path
}

function Get-LatestPerformanceBudgetSummaryPath {
    param(
        [string]$ProjectRootPath,
        [string]$ExcludeSummaryPath
    )
    $baseDirectory = Join-Path $ProjectRootPath 'target\performance-budget-checks'
    if (-not (Test-Path -LiteralPath $baseDirectory)) {
        return ''
    }
    $excludeFullPath = if ([string]::IsNullOrWhiteSpace($ExcludeSummaryPath)) {
        ''
    } else {
        [System.IO.Path]::GetFullPath($ExcludeSummaryPath)
    }

    $latest = Get-ChildItem -LiteralPath $baseDirectory -Directory -ErrorAction SilentlyContinue |
        Sort-Object Name -Descending |
        ForEach-Object {
            $candidate = [System.IO.Path]::GetFullPath((Join-Path $_.FullName 'performance-budget-summary.json'))
            if ((Test-Path -LiteralPath $candidate) -and $candidate -ne $excludeFullPath) {
                return $candidate
            }
        } |
        Select-Object -First 1
    if (-not $latest) {
        return ''
    }
    return (Resolve-Path -LiteralPath $latest).Path
}

function Get-PerformanceBudgetSummaryPaths {
    param(
        [string]$ProjectRootPath,
        [string]$ExcludeSummaryPath,
        [int]$Limit
    )
    $baseDirectory = Join-Path $ProjectRootPath 'target\performance-budget-checks'
    if (-not (Test-Path -LiteralPath $baseDirectory)) {
        return @()
    }
    $excludeFullPath = if ([string]::IsNullOrWhiteSpace($ExcludeSummaryPath)) {
        ''
    } else {
        [System.IO.Path]::GetFullPath($ExcludeSummaryPath)
    }
    $effectiveLimit = if ($Limit -gt 0) { $Limit } else { 1 }

    $paths = New-Object System.Collections.Generic.List[string]
    foreach ($directory in Get-ChildItem -LiteralPath $baseDirectory -Directory -ErrorAction SilentlyContinue | Sort-Object Name -Descending) {
        $candidate = [System.IO.Path]::GetFullPath((Join-Path $directory.FullName 'performance-budget-summary.json'))
        if ((Test-Path -LiteralPath $candidate) -and $candidate -ne $excludeFullPath) {
            $paths.Add((Resolve-Path -LiteralPath $candidate).Path)
        }
        if ($paths.Count -ge $effectiveLimit) {
            break
        }
    }
    return $paths.ToArray()
}

function Get-JsonPathValue {
    param(
        [object]$InputObject,
        [string]$Path
    )
    if ($null -eq $InputObject -or [string]::IsNullOrWhiteSpace($Path)) {
        return $null
    }

    $current = $InputObject
    foreach ($segment in ($Path -split '\.')) {
        if ($null -eq $current) {
            return $null
        }
        $property = $current.PSObject.Properties[$segment]
        if ($null -eq $property) {
            return $null
        }
        $current = $property.Value
    }
    return $current
}

function Read-SurefireSuites {
    param(
        [string]$ProjectRootPath
    )
    $reportDir = Join-Path $ProjectRootPath 'target\surefire-reports'
    $files = Get-ChildItem -LiteralPath $reportDir -Filter 'TEST-*.xml' -File -ErrorAction Stop
    $suites = New-Object System.Collections.Generic.List[object]
    foreach ($file in $files) {
        [xml]$xml = Get-Content -LiteralPath $file.FullName -Raw -Encoding UTF8
        $suite = $xml.testsuite
        if ($null -eq $suite) {
            continue
        }
        $suites.Add([ordered]@{
            name = [string]$suite.name
            path = $file.FullName
            timeSeconds = [double]$suite.time
            tests = [int]$suite.tests
            failures = [int]$suite.failures
            errors = [int]$suite.errors
            skipped = [int]$suite.skipped
        })
    }
    return $suites.ToArray()
}

function Read-SurefireTestCases {
    param(
        [string]$ProjectRootPath
    )
    $reportDir = Join-Path $ProjectRootPath 'target\surefire-reports'
    $files = Get-ChildItem -LiteralPath $reportDir -Filter 'TEST-*.xml' -File -ErrorAction Stop
    $cases = New-Object System.Collections.Generic.List[object]
    foreach ($file in $files) {
        [xml]$xml = Get-Content -LiteralPath $file.FullName -Raw -Encoding UTF8
        $suite = $xml.testsuite
        if ($null -eq $suite -or $null -eq $suite.testcase) {
            continue
        }
        foreach ($testcase in @($suite.testcase)) {
            $cases.Add([ordered]@{
                name = [string]$testcase.name
                className = [string]$testcase.classname
                suiteName = [string]$suite.name
                path = $file.FullName
                timeSeconds = [double]$testcase.time
                failed = $null -ne $testcase.failure
                errored = $null -ne $testcase.error
                skipped = $null -ne $testcase.skipped
            })
        }
    }
    return $cases.ToArray()
}

function Find-SurefireSuites {
    param(
        [object[]]$Suites,
        [object[]]$Patterns
    )
    $matches = New-Object System.Collections.Generic.List[object]
    foreach ($patternValue in @($Patterns)) {
        $pattern = [string]$patternValue
        if ([string]::IsNullOrWhiteSpace($pattern)) {
            continue
        }
        foreach ($suite in $Suites) {
            if ($suite.name -like $pattern -and -not ($matches | Where-Object { $_.path -eq $suite.path })) {
                $matches.Add($suite)
            }
        }
    }
    return $matches.ToArray()
}

function Find-SurefireTestCases {
    param(
        [object[]]$TestCases,
        [object[]]$Specs
    )
    $matches = New-Object System.Collections.Generic.List[object]
    $missing = New-Object System.Collections.Generic.List[string]
    foreach ($spec in @($Specs)) {
        $className = [string]$spec.className
        foreach ($nameValue in @($spec.names)) {
            $testName = [string]$nameValue
            if ([string]::IsNullOrWhiteSpace($className) -or [string]::IsNullOrWhiteSpace($testName)) {
                continue
            }
            $caseMatches = @($TestCases | Where-Object {
                $_.className -like $className -and $_.name -like $testName
            })
            if ($caseMatches.Count -eq 0) {
                $missing.Add(('{0}#{1}' -f $className, $testName))
                continue
            }
            foreach ($testcase in $caseMatches) {
                if (-not ($matches | Where-Object { $_.path -eq $testcase.path -and $_.className -eq $testcase.className -and $_.name -eq $testcase.name })) {
                    $matches.Add($testcase)
                }
            }
        }
    }
    return [pscustomobject]@{
        matches = $matches.ToArray()
        missing = $missing.ToArray()
    }
}

function New-CheckResult {
    param(
        [object]$Budget,
        [string]$Status,
        [string[]]$Messages,
        [object]$Evidence
    )
    return [ordered]@{
        id = [string]$Budget.id
        label = [string]$Budget.label
        kind = [string]$Budget.kind
        required = if ($null -ne $Budget.required) { [bool]$Budget.required } else { $true }
        status = $Status
        messages = @($Messages)
        evidence = $Evidence
    }
}

function Test-SurefireSuiteTimeBudget {
    param(
        [object]$Budget,
        [object[]]$SurefireSuites
    )
    $messages = New-Object System.Collections.Generic.List[string]
    $matches = Find-SurefireSuites -Suites $SurefireSuites -Patterns $Budget.suitePatterns
    $maxSeconds = [double]$Budget.maxSeconds
    $totalSeconds = 0.0
    $tests = 0
    $failures = 0
    $errors = 0
    $skipped = 0
    foreach ($suite in $matches) {
        $totalSeconds += [double]$suite.timeSeconds
        $tests += [int]$suite.tests
        $failures += [int]$suite.failures
        $errors += [int]$suite.errors
        $skipped += [int]$suite.skipped
    }

    if ($matches.Count -eq 0) {
        $messages.Add('No Surefire suites matched this performance budget.')
    }
    if ($maxSeconds -le 0) {
        $messages.Add('Budget maxSeconds must be greater than zero.')
    }
    if ($totalSeconds -gt $maxSeconds) {
        $messages.Add(('Suite time {0:N3}s exceeds budget {1:N3}s.' -f $totalSeconds, $maxSeconds))
    }
    if ($failures -gt 0 -or $errors -gt 0) {
        $messages.Add(('Matched suites contain failures={0}, errors={1}.' -f $failures, $errors))
    }

    $status = if ($messages.Count -eq 0) { 'pass' } else { 'fail' }
    return New-CheckResult -Budget $Budget -Status $status -Messages $messages.ToArray() -Evidence ([ordered]@{
        maxSeconds = $maxSeconds
        totalSeconds = [math]::Round($totalSeconds, 6)
        suiteCount = $matches.Count
        tests = $tests
        failures = $failures
        errors = $errors
        skipped = $skipped
        suites = @($matches)
        suitePatterns = @($Budget.suitePatterns)
    })
}

function Test-SurefireTestCaseTimeBudget {
    param(
        [object]$Budget,
        [object[]]$SurefireTestCases
    )
    $messages = New-Object System.Collections.Generic.List[string]
    $selection = Find-SurefireTestCases -TestCases $SurefireTestCases -Specs $Budget.testcases
    $matches = @($selection.matches)
    $missing = @($selection.missing)
    $maxSeconds = [double]$Budget.maxSeconds
    $totalSeconds = 0.0
    $failed = 0
    $errored = 0
    $skipped = 0
    foreach ($testcase in $matches) {
        $totalSeconds += [double]$testcase.timeSeconds
        if ($testcase.failed) {
            $failed++
        }
        if ($testcase.errored) {
            $errored++
        }
        if ($testcase.skipped) {
            $skipped++
        }
    }

    if ($matches.Count -eq 0) {
        $messages.Add('No Surefire testcases matched this performance budget.')
    }
    foreach ($missingCase in $missing) {
        $messages.Add(("Configured testcase was not found: $missingCase"))
    }
    if ($maxSeconds -le 0) {
        $messages.Add('Budget maxSeconds must be greater than zero.')
    }
    if ($totalSeconds -gt $maxSeconds) {
        $messages.Add(('Testcase time {0:N3}s exceeds budget {1:N3}s.' -f $totalSeconds, $maxSeconds))
    }
    if ($failed -gt 0 -or $errored -gt 0) {
        $messages.Add(('Matched testcases contain failed={0}, errored={1}.' -f $failed, $errored))
    }

    $status = if ($messages.Count -eq 0) { 'pass' } else { 'fail' }
    return New-CheckResult -Budget $Budget -Status $status -Messages $messages.ToArray() -Evidence ([ordered]@{
        maxSeconds = $maxSeconds
        totalSeconds = [math]::Round($totalSeconds, 6)
        testcaseCount = $matches.Count
        failed = $failed
        errored = $errored
        skipped = $skipped
        missing = $missing
        testcases = @($matches)
    })
}

function Test-SmokeArtifactSizeBudget {
    param(
        [object]$Budget,
        [object]$SmokeSummary,
        [string]$SmokeSummaryPath,
        [switch]$AllowMissing
    )
    $messages = New-Object System.Collections.Generic.List[string]
    if ($null -eq $SmokeSummary) {
        $message = 'Smoke summary is not available for this performance budget.'
        if ($AllowMissing) {
            return New-CheckResult -Budget $Budget -Status 'not_included' -Messages @($message) -Evidence ([ordered]@{
                smokeSummaryPath = $SmokeSummaryPath
            })
        }
        return New-CheckResult -Budget $Budget -Status 'fail' -Messages @($message) -Evidence ([ordered]@{
            smokeSummaryPath = $SmokeSummaryPath
        })
    }

    if (-not [string]::IsNullOrWhiteSpace([string]$Budget.requiredStatusPath)) {
        $actualStatus = [string](Get-JsonPathValue -InputObject $SmokeSummary -Path ([string]$Budget.requiredStatusPath))
        $expectedStatus = [string]$Budget.requiredStatus
        if ($actualStatus -ne $expectedStatus) {
            $messages.Add(('Smoke status {0} expected `{1}` but was `{2}`.' -f $Budget.requiredStatusPath, $expectedStatus, $actualStatus))
        }
    }

    $artifactEvidence = New-Object System.Collections.Generic.List[object]
    foreach ($artifact in @($Budget.artifacts)) {
        $jsonPath = [string]$artifact.jsonPath
        $artifactPath = [string](Get-JsonPathValue -InputObject $SmokeSummary -Path $jsonPath)
        $resolvedArtifactPath = Resolve-OptionalExistingPath -Path $artifactPath
        if ([string]::IsNullOrWhiteSpace($resolvedArtifactPath)) {
            $messages.Add(('Artifact `{0}` missing at smoke path `{1}`.' -f $artifact.label, $jsonPath))
            $artifactEvidence.Add([ordered]@{
                label = [string]$artifact.label
                jsonPath = $jsonPath
                path = $artifactPath
                status = 'missing'
                bytes = 0
                minBytes = if ($null -ne $artifact.minBytes) { [long]$artifact.minBytes } else { 0 }
                maxBytes = if ($null -ne $artifact.maxBytes) { [long]$artifact.maxBytes } else { 0 }
            })
            continue
        }

        $bytes = (Get-Item -LiteralPath $resolvedArtifactPath).Length
        $minBytes = if ($null -ne $artifact.minBytes) { [long]$artifact.minBytes } else { 0 }
        $maxBytes = if ($null -ne $artifact.maxBytes) { [long]$artifact.maxBytes } else { 0 }
        if ($minBytes -gt 0 -and $bytes -lt $minBytes) {
            $messages.Add(('Artifact `{0}` size {1} bytes is below minimum {2} bytes.' -f $artifact.label, $bytes, $minBytes))
        }
        if ($maxBytes -gt 0 -and $bytes -gt $maxBytes) {
            $messages.Add(('Artifact `{0}` size {1} bytes exceeds budget {2} bytes.' -f $artifact.label, $bytes, $maxBytes))
        }
        $artifactEvidence.Add([ordered]@{
            label = [string]$artifact.label
            jsonPath = $jsonPath
            path = $resolvedArtifactPath
            status = 'present'
            bytes = $bytes
            minBytes = $minBytes
            maxBytes = $maxBytes
        })
    }

    $status = if ($messages.Count -eq 0) { 'pass' } else { 'fail' }
    return New-CheckResult -Budget $Budget -Status $status -Messages $messages.ToArray() -Evidence ([ordered]@{
        smokeSummaryPath = $SmokeSummaryPath
        artifacts = $artifactEvidence.ToArray()
    })
}

function Get-BudgetById {
    param(
        [object]$BudgetConfig,
        [string]$Id
    )
    return @($BudgetConfig.budgets | Where-Object { [string]$_.id -eq $Id } | Select-Object -First 1)
}

function Get-CheckById {
    param(
        [object]$Summary,
        [string]$Id
    )
    if ($null -eq $Summary) {
        return $null
    }
    return @($Summary.checks | Where-Object { [string]$_.id -eq $Id } | Select-Object -First 1)
}

function Get-ArtifactByLabel {
    param(
        [object[]]$Artifacts,
        [string]$Label
    )
    return @($Artifacts | Where-Object { [string]$_.label -eq $Label } | Select-Object -First 1)
}

function Get-RegressionMode {
    param(
        [object]$Config
    )
    $mode = [string]$Config.regressionMode
    if ([string]::IsNullOrWhiteSpace($mode)) {
        return 'warn'
    }
    $normalized = $mode.Trim().ToLowerInvariant()
    if ($normalized -in @('warn', 'error', 'ignore')) {
        return $normalized
    }
    return 'warn'
}

function Convert-ModeToStatus {
    param(
        [string]$Mode
    )
    if ($Mode -eq 'error') {
        return 'error'
    }
    if ($Mode -eq 'ignore') {
        return 'ok'
    }
    return 'warning'
}

function Format-SummaryTimestamp {
    param(
        [object]$Value
    )
    if ($null -eq $Value) {
        return ''
    }
    if ($Value -is [datetime]) {
        return $Value.ToString('o')
    }
    return [string]$Value
}

function Get-NumericMedian {
    param(
        [double[]]$Values
    )
    $cleanValues = @($Values | Where-Object { $null -ne $_ } | Sort-Object)
    if ($cleanValues.Count -eq 0) {
        return $null
    }
    $middle = [int][math]::Floor($cleanValues.Count / 2)
    if ($cleanValues.Count % 2 -eq 1) {
        return [double]$cleanValues[$middle]
    }
    return (([double]$cleanValues[$middle - 1] + [double]$cleanValues[$middle]) / 2.0)
}

function Get-AggregatedNumber {
    param(
        [double[]]$Values,
        [string]$Aggregation
    )
    $cleanValues = @($Values | Where-Object { $null -ne $_ })
    if ($cleanValues.Count -eq 0) {
        return $null
    }
    switch ($Aggregation.Trim().ToLowerInvariant()) {
        'median' {
            return Get-NumericMedian -Values $cleanValues
        }
        'latest' {
            return [double]$cleanValues[0]
        }
        default {
            return Get-NumericMedian -Values $cleanValues
        }
    }
}

function Read-PerformanceBudgetSummaries {
    param(
        [string[]]$Paths
    )
    $entries = New-Object System.Collections.Generic.List[object]
    foreach ($path in @($Paths)) {
        if ([string]::IsNullOrWhiteSpace($path) -or -not (Test-Path -LiteralPath $path)) {
            continue
        }
        $entries.Add([pscustomobject]@{
            path = (Resolve-Path -LiteralPath $path).Path
            summary = (Get-Content -LiteralPath $path -Raw -Encoding UTF8 | ConvertFrom-Json)
        })
    }
    return $entries.ToArray()
}

function New-RegressionComparison {
    param(
        [string]$BudgetId,
        [string]$Metric,
        [double]$CurrentValue,
        [double]$BaselineValue,
        [double]$AllowedDelta,
        [string]$Unit,
        [string]$Mode,
        [string]$Aggregation,
        [int]$BaselineSamples
    )
    $delta = $CurrentValue - $BaselineValue
    $deltaPercent = if ($BaselineValue -gt 0) {
        ($delta / $BaselineValue) * 100.0
    } else {
        0.0
    }
    $status = if ($BaselineValue -le 0 -or $delta -le $AllowedDelta) {
        'ok'
    } else {
        Convert-ModeToStatus -Mode $Mode
    }
    return [ordered]@{
        budgetId = $BudgetId
        metric = $Metric
        unit = $Unit
        mode = $Mode
        status = $status
        baseline = [math]::Round($BaselineValue, 6)
        current = [math]::Round($CurrentValue, 6)
        delta = [math]::Round($delta, 6)
        deltaPercent = [math]::Round($deltaPercent, 2)
        allowedDelta = [math]::Round($AllowedDelta, 6)
        aggregation = $Aggregation
        baselineSamples = $BaselineSamples
    }
}

function Compare-WithBaseline {
    param(
        [object]$BudgetConfig,
        [object[]]$Checks,
        [object[]]$BaselineEntries,
        [string[]]$BaselineSummaryPaths,
        [string]$Aggregation,
        [int]$WindowSize,
        [string]$BaselineMode
    )
    $comparisons = New-Object System.Collections.Generic.List[object]
    $warnings = New-Object System.Collections.Generic.List[string]
    $errors = New-Object System.Collections.Generic.List[string]

    $entries = @($BaselineEntries | Where-Object { $null -ne $_ })
    $summaryPaths = @($BaselineSummaryPaths | Where-Object { -not [string]::IsNullOrWhiteSpace([string]$_) })
    if ($entries.Count -eq 0) {
        $warnings.Add('No previous performance budget summary was available for trend comparison.')
        return [ordered]@{
            status = 'missing'
            mode = $BaselineMode
            aggregation = $Aggregation
            windowSize = $WindowSize
            sampleCount = 0
            summaryPath = ''
            summaryPaths = @()
            checkedAt = ''
            comparisons = @()
            warnings = $warnings.ToArray()
            errors = $errors.ToArray()
        }
    }

    foreach ($check in @($Checks)) {
        if ($check.status -eq 'not_included') {
            continue
        }
        $budget = Get-BudgetById -BudgetConfig $BudgetConfig -Id ([string]$check.id)

        if ($check.kind -in @('surefire-suite-time', 'surefire-testcase-time')) {
            if ($null -eq $budget.maxRegressionPercent) {
                continue
            }
            $mode = Get-RegressionMode -Config $budget
            if ($mode -eq 'ignore') {
                continue
            }
            $baselineValues = New-Object System.Collections.Generic.List[double]
            foreach ($entry in $entries) {
                $baselineCheck = Get-CheckById -Summary $entry.summary -Id ([string]$check.id)
                if ($null -ne $baselineCheck -and $baselineCheck.status -ne 'not_included' -and $null -ne $baselineCheck.evidence.totalSeconds) {
                    $baselineValues.Add([double]$baselineCheck.evidence.totalSeconds)
                }
            }
            if ($baselineValues.Count -eq 0) {
                $warnings.Add(("No comparable baseline check for budget `{0}`." -f $check.id))
                continue
            }
            $baselineValue = [double](Get-AggregatedNumber -Values $baselineValues.ToArray() -Aggregation $Aggregation)
            $currentValue = [double]$check.evidence.totalSeconds
            $percentDelta = $baselineValue * ([double]$budget.maxRegressionPercent / 100.0)
            $minDelta = if ($null -ne $budget.minRegressionDeltaSeconds) { [double]$budget.minRegressionDeltaSeconds } else { 0.0 }
            $comparison = New-RegressionComparison `
                -BudgetId ([string]$check.id) `
                -Metric 'totalSeconds' `
                -CurrentValue $currentValue `
                -BaselineValue $baselineValue `
                -AllowedDelta ([math]::Max($percentDelta, $minDelta)) `
                -Unit 'seconds' `
                -Mode $mode `
                -Aggregation $Aggregation `
                -BaselineSamples $baselineValues.Count
            $comparisons.Add($comparison)
        } elseif ($check.kind -eq 'smoke-artifact-size') {
            $currentArtifacts = @($check.evidence.artifacts | Where-Object { $null -ne $_ })
            foreach ($artifact in @($budget.artifacts)) {
                if ($null -eq $artifact.maxRegressionPercent) {
                    continue
                }
                $mode = Get-RegressionMode -Config $artifact
                if ($mode -eq 'ignore') {
                    continue
                }
                $currentArtifact = Get-ArtifactByLabel -Artifacts $currentArtifacts -Label ([string]$artifact.label)
                if ($null -eq $currentArtifact) {
                    $warnings.Add(("No current artifact `{0}` for budget `{1}`." -f $artifact.label, $check.id))
                    continue
                }
                $baselineValues = New-Object System.Collections.Generic.List[double]
                foreach ($entry in $entries) {
                    $baselineCheck = Get-CheckById -Summary $entry.summary -Id ([string]$check.id)
                    if ($null -eq $baselineCheck -or $baselineCheck.status -eq 'not_included') {
                        continue
                    }
                    $baselineArtifacts = @($baselineCheck.evidence.artifacts | Where-Object { $null -ne $_ })
                    $baselineArtifact = Get-ArtifactByLabel -Artifacts $baselineArtifacts -Label ([string]$artifact.label)
                    if ($null -ne $baselineArtifact -and $null -ne $baselineArtifact.bytes) {
                        $baselineValues.Add([double]$baselineArtifact.bytes)
                    }
                }
                if ($baselineValues.Count -eq 0) {
                    $warnings.Add(("No comparable baseline artifact `{0}` for budget `{1}`." -f $artifact.label, $check.id))
                    continue
                }
                $baselineValue = [double](Get-AggregatedNumber -Values $baselineValues.ToArray() -Aggregation $Aggregation)
                $currentValue = [double]$currentArtifact.bytes
                $percentDelta = $baselineValue * ([double]$artifact.maxRegressionPercent / 100.0)
                $minDelta = if ($null -ne $artifact.minRegressionDeltaBytes) { [double]$artifact.minRegressionDeltaBytes } else { 0.0 }
                $comparison = New-RegressionComparison `
                    -BudgetId ([string]$check.id) `
                    -Metric ('{0}.bytes' -f $artifact.label) `
                    -CurrentValue $currentValue `
                    -BaselineValue $baselineValue `
                    -AllowedDelta ([math]::Max($percentDelta, $minDelta)) `
                    -Unit 'bytes' `
                    -Mode $mode `
                    -Aggregation $Aggregation `
                    -BaselineSamples $baselineValues.Count
                $comparisons.Add($comparison)
            }
        }
    }

    foreach ($comparison in $comparisons) {
        if ($comparison.status -eq 'error') {
            $errors.Add(("Performance regression for `{0}` metric `{1}`: current={2} {3}, baseline={4} {3}, allowed delta={5}." -f $comparison.budgetId, $comparison.metric, $comparison.current, $comparison.unit, $comparison.baseline, $comparison.allowedDelta))
        } elseif ($comparison.status -eq 'warning') {
            $warnings.Add(("Performance trend warning for `{0}` metric `{1}`: current={2} {3}, baseline={4} {3}, allowed delta={5}." -f $comparison.budgetId, $comparison.metric, $comparison.current, $comparison.unit, $comparison.baseline, $comparison.allowedDelta))
        }
    }

    $status = if ($errors.Count -gt 0) {
        'error'
    } elseif ($warnings.Count -gt 0) {
        'warning'
    } else {
        'ok'
    }
    return [ordered]@{
        status = $status
        mode = $BaselineMode
        aggregation = $Aggregation
        windowSize = $WindowSize
        sampleCount = $entries.Count
        summaryPath = if ($summaryPaths.Count -gt 0) { [string]$summaryPaths[0] } else { '' }
        summaryPaths = $summaryPaths
        checkedAt = if ($entries.Count -gt 0) { Format-SummaryTimestamp -Value $entries[0].summary.checkedAt } else { '' }
        comparisons = $comparisons.ToArray()
        warnings = $warnings.ToArray()
        errors = $errors.ToArray()
    }
}

if ([string]::IsNullOrWhiteSpace($ProjectRoot)) {
    $ProjectRoot = Resolve-Path (Join-Path $PSScriptRoot '..')
}
$projectRootPath = Resolve-RequiredPath -Path $ProjectRoot -Label 'Project root'
$targetDirectory = Join-Path $projectRootPath 'target'

$budgetConfigPath = if ([string]::IsNullOrWhiteSpace($BudgetConfigPath)) {
    Resolve-RequiredPath -Path (Join-Path $projectRootPath 'scripts\starcore-performance-budgets.json') -Label 'Performance budget config'
} else {
    Resolve-RequiredPath -Path $BudgetConfigPath -Label 'Performance budget config'
}

$smokeSummaryPath = if ([string]::IsNullOrWhiteSpace($SmokeSummaryJsonPath)) {
    if ($AllowMissingSmoke) {
        ''
    } else {
        $latest = Get-LatestFilePath -BaseDirectory $targetDirectory -DirectoryPattern 'smoke-harness-*' -FileName 'smoke-summary.json'
        if ([string]::IsNullOrWhiteSpace($latest)) {
            ''
        } else {
            $latest
        }
    }
} else {
    Resolve-RequiredPath -Path $SmokeSummaryJsonPath -Label 'Smoke summary JSON'
}

if ([string]::IsNullOrWhiteSpace($OutputDir)) {
    $OutputDir = Join-Path $targetDirectory ('performance-budget-checks\check-' + (Get-Date -Format 'yyyyMMdd-HHmmss'))
}
$outputDirPath = Ensure-Directory -Path $OutputDir
$summaryPath = Join-Path $outputDirPath 'performance-budget-summary.json'
$budgetConfig = Get-Content -LiteralPath $budgetConfigPath -Raw -Encoding UTF8 | ConvertFrom-Json
$configuredWindowSize = if ($budgetConfig.trend -and $null -ne $budgetConfig.trend.baselineWindowSize -and [int]$budgetConfig.trend.baselineWindowSize -gt 0) {
    [int]$budgetConfig.trend.baselineWindowSize
} else {
    1
}
$effectiveBaselineWindowSize = if ($BaselineWindowSize -gt 0) { $BaselineWindowSize } else { $configuredWindowSize }
$effectiveBaselineAggregation = if (-not [string]::IsNullOrWhiteSpace($BaselineAggregation)) {
    $BaselineAggregation.Trim().ToLowerInvariant()
} elseif ($budgetConfig.trend -and -not [string]::IsNullOrWhiteSpace([string]$budgetConfig.trend.aggregation)) {
    ([string]$budgetConfig.trend.aggregation).Trim().ToLowerInvariant()
} else {
    'median'
}
$baselineMode = if ([string]::IsNullOrWhiteSpace($BaselineSummaryPath)) { 'history' } else { 'explicit' }
[string[]]$baselineSummaryPaths = if ([string]::IsNullOrWhiteSpace($BaselineSummaryPath)) {
    @(Get-PerformanceBudgetSummaryPaths -ProjectRootPath $projectRootPath -ExcludeSummaryPath $summaryPath -Limit $effectiveBaselineWindowSize)
} else {
    @((Resolve-RequiredPath -Path $BaselineSummaryPath -Label 'Performance budget baseline summary JSON'))
}
$baselineSummaryPath = if (@($baselineSummaryPaths).Count -gt 0) { [string]@($baselineSummaryPaths)[0] } else { '' }
$baselineEntries = Read-PerformanceBudgetSummaries -Paths $baselineSummaryPaths
$surefireSuites = Read-SurefireSuites -ProjectRootPath $projectRootPath
$surefireTestCases = Read-SurefireTestCases -ProjectRootPath $projectRootPath
$smokeSummary = $null
if (-not [string]::IsNullOrWhiteSpace($smokeSummaryPath)) {
    $smokeSummary = Get-Content -LiteralPath $smokeSummaryPath -Raw -Encoding UTF8 | ConvertFrom-Json
}

$checks = New-Object System.Collections.Generic.List[object]
foreach ($budget in @($budgetConfig.budgets)) {
    switch ([string]$budget.kind) {
        'surefire-suite-time' {
            $checks.Add((Test-SurefireSuiteTimeBudget -Budget $budget -SurefireSuites $surefireSuites))
        }
        'surefire-testcase-time' {
            $checks.Add((Test-SurefireTestCaseTimeBudget -Budget $budget -SurefireTestCases $surefireTestCases))
        }
        'smoke-artifact-size' {
            $checks.Add((Test-SmokeArtifactSizeBudget -Budget $budget -SmokeSummary $smokeSummary -SmokeSummaryPath $smokeSummaryPath -AllowMissing:$AllowMissingSmoke))
        }
        default {
            $checks.Add((New-CheckResult -Budget $budget -Status 'fail' -Messages @("Unsupported performance budget kind: $($budget.kind)") -Evidence ([ordered]@{})))
        }
    }
}

$failures = @($checks | Where-Object { $_.status -eq 'fail' })
$notIncluded = @($checks | Where-Object { $_.status -eq 'not_included' })
$baseline = Compare-WithBaseline `
    -BudgetConfig $budgetConfig `
    -Checks $checks.ToArray() `
    -BaselineEntries $baselineEntries `
    -BaselineSummaryPaths $baselineSummaryPaths `
    -Aggregation $effectiveBaselineAggregation `
    -WindowSize $effectiveBaselineWindowSize `
    -BaselineMode $baselineMode
$status = if ($failures.Count -gt 0) {
    'error'
} elseif ($baseline.status -eq 'error') {
    'error'
} elseif ($notIncluded.Count -gt 0) {
    'warning'
} elseif ($baseline.status -in @('warning', 'missing')) {
    'warning'
} else {
    'ok'
}

$summary = [ordered]@{
    checkedAt = (Get-Date).ToString('o')
    status = $status
    projectRoot = $projectRootPath
    budgetConfigPath = $budgetConfigPath
    smokeSummaryPath = $smokeSummaryPath
    baselineSummaryPath = $baselineSummaryPath
    baselineSummaryPaths = @($baselineSummaryPaths)
    surefireReportRoot = Join-Path $projectRootPath 'target\surefire-reports'
    totals = [ordered]@{
        checks = $checks.Count
        passed = @($checks | Where-Object { $_.status -eq 'pass' }).Count
        failed = $failures.Count
        notIncluded = $notIncluded.Count
    }
    baseline = $baseline
    checks = $checks.ToArray()
    summaryPath = $summaryPath
}
$summary | ConvertTo-Json -Depth 10 | Set-Content -LiteralPath $summaryPath -Encoding UTF8

[pscustomobject]$summary | ConvertTo-Json -Depth 10
