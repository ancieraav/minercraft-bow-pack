[CmdletBinding()]
param([Parameter(Mandatory = $true)][string] $BuildDirectory)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
$buildRoot = (Resolve-Path -LiteralPath $BuildDirectory).ProviderPath
if (Test-Path -LiteralPath (Join-Path $buildRoot '.git')) {
    throw 'Use a disposable build copy, not the Git checkout, for this profiling workload.'
}
$source = Join-Path $buildRoot 'src/gametest/java/dev/bowpack/gametest/UnstopableBenchmark.java'
$manifest = Join-Path $buildRoot 'src/gametest/resources/fabric.mod.json'
$init = Join-Path $buildRoot 'bowpack-profile.gradle'
if ((Test-Path -LiteralPath $source) -or (Test-Path -LiteralPath $init)) {
    throw 'The profiling copy already contains benchmark files; use a fresh copy.'
}
$originalManifest = [IO.File]::ReadAllBytes($manifest)
try {
    Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'UnstopableBenchmark.java') -Destination $source
    $document = Get-Content -LiteralPath $manifest -Raw | ConvertFrom-Json
    $document.entrypoints.'fabric-gametest' += 'dev.bowpack.gametest.UnstopableBenchmark'
    [IO.File]::WriteAllText($manifest, ($document | ConvertTo-Json -Depth 20), [Text.UTF8Encoding]::new($false))
    $gradle = @'
allprojects {
    afterEvaluate {
        tasks.named('runGameTest') {
            jvmArgs '-Dfabric-api.gametest.filter=bowpack-gametest:unstopable_benchmark_compare_eighty_arrow_block_destruction'
        }
    }
}
'@
    [IO.File]::WriteAllText($init, $gradle, [Text.UTF8Encoding]::new($false))
    & (Join-Path $buildRoot 'scripts/build.ps1') runGameTest --init-script $init --console=plain
}
finally {
    [IO.File]::WriteAllBytes($manifest, $originalManifest)
    foreach ($path in @($source, $init)) {
        if (Test-Path -LiteralPath $path) { Remove-Item -LiteralPath $path -Force }
    }
}
