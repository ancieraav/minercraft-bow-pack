[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string] $BaselineDirectory,
    [Parameter(Mandatory = $true)][string] $UpdatedDirectory
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
$measuredRounds = 30
$ticksPerRound = 20

function Read-Benchmark([string] $Directory, [string] $Version) {
    $tickRows = @(Import-Csv -LiteralPath (Join-Path $Directory 'ticks.csv'))
    $blockRows = @(Import-Csv -LiteralPath (Join-Path $Directory 'blocks.csv'))
    if ($tickRows.Count -ne 2 * $measuredRounds * $ticksPerRound -or $blockRows.Count -ne 2 * $measuredRounds) {
        throw "$Version must contain 600 processing slices and 30 block totals per Follow setting."
    }
    $seenTicks = @{}
    $blocks = @{}
    $samples = foreach ($row in $tickRows) {
        if ($row.follow -cnotin @('false', 'true')) { throw "$Version has an invalid Follow setting." }
        $round = [int]$row.round
        $tick = [int]$row.tick
        $nanoseconds = [long]$row.nanoseconds
        $allocated = [long]$row.allocated_bytes
        if ($round -lt 0 -or $round -ge $measuredRounds -or $tick -lt 0 -or $tick -ge $ticksPerRound -or
                $nanoseconds -le 0 -or $allocated -lt 0) {
            throw "$Version has an invalid measurement at Follow=$($row.follow), round=$round, tick=$tick."
        }
        $key = "$($row.follow):${round}:$tick"
        if ($seenTicks.ContainsKey($key)) { throw "$Version contains duplicate processing slice $key." }
        $seenTicks.Add($key, $true)
        [pscustomobject]@{ Follow = $row.follow; Round = $round; Tick = $tick;
            Nanoseconds = $nanoseconds; AllocatedBytes = $allocated }
    }
    foreach ($row in $blockRows) {
        if ($row.follow -cnotin @('false', 'true')) { throw "$Version has an invalid Follow setting." }
        $round = [int]$row.round
        $broken = [int]$row.broken_blocks
        if ($round -lt 0 -or $round -ge $measuredRounds -or $broken -le 0 -or $broken -gt 1280) {
            throw "$Version has an invalid block total at Follow=$($row.follow), round=$round."
        }
        $key = "$($row.follow):$round"
        if ($blocks.ContainsKey($key)) { throw "$Version contains duplicate block total $key." }
        $blocks.Add($key, $broken)
    }
    # Counts plus unique, bounded coordinates guarantee that every expected slice exists.
    return @{ Version = $Version; Samples = @($samples); Blocks = $blocks }
}

function Get-Summary($Run, [string] $Follow, [int] $FirstRound, [int] $LastRound) {
    $samples = @($Run.Samples | Where-Object {
        $_.Follow -eq $Follow -and $_.Round -ge $FirstRound -and $_.Round -le $LastRound
    })
    $times = @($samples | ForEach-Object { $_.Nanoseconds / 1000000.0 } | Sort-Object)
    $middle = [int][Math]::Floor($times.Count / 2)
    $median = if ($times.Count % 2 -eq 0) { ($times[$middle - 1] + $times[$middle]) / 2 } else { $times[$middle] }
    $allocated = ($samples | Measure-Object -Property AllocatedBytes -Sum).Sum
    $broken = 0
    for ($round = $FirstRound; $round -le $LastRound; $round++) { $broken += $Run.Blocks["${Follow}:$round"] }
    [pscustomobject]@{
        RoundRange = "$FirstRound-$LastRound"
        ProcessingSlices = $samples.Count
        BrokenBlocks = $broken
        MedianMilliseconds = [Math]::Round($median, 4)
        P95Milliseconds = [Math]::Round($times[[int][Math]::Ceiling(0.95 * $times.Count) - 1], 4)
        MeanMilliseconds = [Math]::Round(($times | Measure-Object -Average).Average, 4)
        AllocatedBytes = $allocated
        AllocatedMiB = [Math]::Round($allocated / 1MB, 3)
        AllocatedBytesPerSlice = [Math]::Round($allocated / $samples.Count, 2)
        AllocatedBytesPerBrokenBlock = [Math]::Round($allocated / $broken, 2)
    }
}

$baseline = Read-Benchmark $BaselineDirectory '1.3.0'
$updated = Read-Benchmark $UpdatedDirectory '1.4.0'
foreach ($key in $baseline.Blocks.Keys) {
    if ($baseline.Blocks[$key] -ne $updated.Blocks[$key]) {
        throw "Benchmark destruction differs at Follow:round=$key; baseline=$($baseline.Blocks[$key]), updated=$($updated.Blocks[$key])."
    }
}
$results = @(foreach ($run in @($baseline, $updated)) {
    foreach ($follow in @('false', 'true')) {
        $first = Get-Summary $run $follow 0 14
        $last = Get-Summary $run $follow 15 29
        $shift = 100 * ($last.MedianMilliseconds / $first.MedianMilliseconds - 1)
        [pscustomobject]@{
            Version = $run.Version
            Follow = $follow
            FullRun = Get-Summary $run $follow 0 29
            FirstHalf = $first
            LastHalf = $last
            LastHalfMedianChangePercent = [Math]::Round($shift, 2)
            NotableWithinRunDrift = [Math]::Abs($shift) -ge 10
        }
    }
})
$comparisons = @(foreach ($follow in @('false', 'true')) {
    $before = $results | Where-Object { $_.Version -eq '1.3.0' -and $_.Follow -eq $follow }
    $after = $results | Where-Object { $_.Version -eq '1.4.0' -and $_.Follow -eq $follow }
    foreach ($window in @('FullRun', 'LastHalf')) {
        $old = $before.$window
        $new = $after.$window
        $allocationChange = if ($old.AllocatedBytes -gt 0) {
            [Math]::Round(100 * (1 - $new.AllocatedBytes / $old.AllocatedBytes), 2)
        } else { $null }
        [pscustomobject]@{
            Follow = $follow
            Window = $window
            BrokenBlocks = $old.BrokenBlocks
            MedianTimeReductionPercent = [Math]::Round(100 * (1 - $new.MedianMilliseconds / $old.MedianMilliseconds), 2)
            AllocationReductionPercent = $allocationChange
        }
    }
})
[pscustomobject]@{
    Measurement = 'Elapsed time and server-thread allocation while manually ticking up to 80 live arrows in sequence.'
    Limitations = 'Excludes wall setup, regular world ticks, deferred lighting, network delivery, and client rendering; not server MSPT or FPS. One process per version is observational, not a statistical performance guarantee.'
    DriftCriterion = 'Absolute median change of at least 10% between the first and last 15 rounds flags possible warmup or run drift; it does not identify the cause.'
    WarmupRoundsPerCase = 50
    MeasuredRoundsPerCase = $measuredRounds
    ProcessingSlicesPerCase = $measuredRounds * $ticksPerRound
    Results = $results
    Comparisons = $comparisons
} | ConvertTo-Json -Depth 6
