[CmdletBinding()]
param(
    [Parameter(ValueFromRemainingArguments = $true)]
    [string[]] $Tasks = @('test', 'build')
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
$projectRoot = Split-Path -Parent $PSScriptRoot
$wrapper = Join-Path $projectRoot 'gradlew.bat'
if (-not (Test-Path -LiteralPath $wrapper -PathType Leaf)) { throw 'Gradle wrapper belum tersedia di folder proyek.' }

$candidateHomes = @()
if (-not [string]::IsNullOrWhiteSpace($env:JAVA_HOME)) { $candidateHomes += $env:JAVA_HOME }
$compilerOnPath = Get-Command javac.exe -CommandType Application -ErrorAction SilentlyContinue | Select-Object -First 1
if ($null -ne $compilerOnPath) { $candidateHomes += Split-Path -Parent (Split-Path -Parent $compilerOnPath.Source) }
$candidateHomes += Join-Path $env:LOCALAPPDATA 'Packages/Microsoft.4297127D64EC6_8wekyb3d8bbwe/LocalCache/Local/runtime/java-runtime-epsilon/windows-x64/java-runtime-epsilon'
$selectedJdk = $null
foreach ($candidate in $candidateHomes | Select-Object -Unique) {
    $compiler = Join-Path $candidate 'bin/javac.exe'
    $java = Join-Path $candidate 'bin/java.exe'
    if (-not (Test-Path -LiteralPath $compiler -PathType Leaf) -or -not (Test-Path -LiteralPath $java -PathType Leaf)) { continue }
    $version = & $compiler -version
    if ($LASTEXITCODE -eq 0 -and ($version -join ' ') -match '^javac 25(?:\.|\s|$)') {
        $selectedJdk = (Resolve-Path -LiteralPath $candidate).ProviderPath
        break
    }
}
if ($null -eq $selectedJdk) { throw 'JDK 25 tidak ditemukan. Siapkan JDK 25 melalui JAVA_HOME atau runtime Minecraft Launcher terlebih dahulu.' }

$previousJavaHome = [Environment]::GetEnvironmentVariable('JAVA_HOME', 'Process')
$previousPath = [Environment]::GetEnvironmentVariable('PATH', 'Process')
Push-Location $projectRoot
try {
    $env:JAVA_HOME = $selectedJdk
    $env:PATH = (Join-Path $selectedJdk 'bin') + [IO.Path]::PathSeparator + $previousPath
    Write-Output "Menggunakan JDK 25: $selectedJdk"
    & $wrapper @Tasks
    if ($LASTEXITCODE -ne 0) { throw "Build gagal (kode $LASTEXITCODE). Periksa pesan Gradle di atas." }
}
finally {
    $env:JAVA_HOME = $previousJavaHome
    $env:PATH = $previousPath
    Pop-Location
}
