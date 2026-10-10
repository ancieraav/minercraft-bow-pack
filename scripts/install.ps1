[CmdletBinding()]
param(
    [string] $ModJar,
    [string] $MinecraftDirectory = (Join-Path $env:APPDATA '.minecraft'),
    [switch] $ValidateOnly
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
if (-not $PSBoundParameters.ContainsKey('ModJar')) {
    $ModJar = Join-Path $PSScriptRoot '../build/libs/bow-pack-1.4.0.jar'
}
. (Join-Path $PSScriptRoot 'InstallSupport.ps1')
Add-Type -AssemblyName System.IO.Compression.FileSystem
[Net.ServicePointManager]::SecurityProtocol = [Net.ServicePointManager]::SecurityProtocol -bor [Net.SecurityProtocolType]::Tls12
$gameVersion = '26.3'
$loaderVersion = '0.19.5'
$apiVersion = '0.161.0+26.3'
$profileId = "fabric-loader-$loaderVersion-$gameVersion"
$launcherProfileKey = 'bowpack-26.3'
$stageDirectory = $null

try {
    $minecraftRoot = (Resolve-Path -LiteralPath $MinecraftDirectory).ProviderPath
    $sourceJar = (Resolve-Path -LiteralPath $ModJar).ProviderPath
    $modInfo = Get-ModInfo $sourceJar
    if ($null -eq $modInfo -or $modInfo.id -ne 'bowpack' -or $modInfo.version -notmatch '^[a-zA-Z0-9.+_-]+$') {
        throw 'Berkas yang dipilih bukan paket Bow Pack dengan versi yang valid.'
    }
    $launcherPaths = @('launcher_profiles.json', 'launcher_profiles_microsoft_store.json') |
        Where-Object { Test-Path -LiteralPath (Join-Path $minecraftRoot $_) -PathType Leaf }
    if (@($launcherPaths).Count -eq 0) { throw 'Buka Minecraft Launcher setidaknya sekali sebelum memasang Bow Pack.' }
    $launcherDocuments = @{}
    $launcherHashes = @{}
    foreach ($relativePath in $launcherPaths) {
        $launcherPath = Join-Path $minecraftRoot $relativePath
        $document = Get-Content -LiteralPath $launcherPath -Raw | ConvertFrom-Json
        if ($null -eq $document -or $document -isnot [pscustomobject]) { throw "JSON launcher tidak valid: $relativePath" }
        $launcherDocuments[$relativePath] = $document
        $launcherHashes[$relativePath] = (Get-FileHash -LiteralPath $launcherPath -Algorithm SHA256).Hash
    }
    $oldModFiles = @()
    $modsPath = Join-Path $minecraftRoot 'mods'
    $modName = "bow-pack-$($modInfo.version).jar"
    $apiName = "fabric-api-$apiVersion.jar"
    if (Test-Path -LiteralPath $modsPath) {
        foreach ($file in Get-ChildItem -LiteralPath $modsPath -Filter '*.jar' -File) {
            $info = Get-ModInfo $file.FullName
            if ($null -ne $info -and $info.id -in @('bowpack', 'fabric-api')) { $oldModFiles += $file.Name }
            elseif ($file.Name -in @($modName, $apiName)) { throw "Nama berkas tujuan dipakai oleh mod lain: $($file.Name)" }
        }
    }
    if ($ValidateOnly) {
        Write-Output "Validasi berhasil: Bow Pack $($modInfo.version), Minecraft $gameVersion, Fabric $loaderVersion."
        Write-Output "Tujuan: $minecraftRoot. Profil: Bow Pack 26.3. Tidak ada perubahan berkas."
        return
    }
    Assert-MinecraftClosed
    $stageDirectory = Join-Path ([IO.Path]::GetTempPath()) ('bowpack-install-' + [Guid]::NewGuid().ToString('N'))
    $null = [IO.Directory]::CreateDirectory($stageDirectory)
    Write-Output 'Mengunduh dan memeriksa dependensi Fabric...'
    $fabricProfile = Invoke-RestMethod -Uri "https://meta.fabricmc.net/v2/versions/loader/$gameVersion/$loaderVersion/profile/json"
    if ($fabricProfile.id -ne $profileId -or $fabricProfile.inheritsFrom -ne $gameVersion -or
        $fabricProfile.mainClass -ne 'net.fabricmc.loader.impl.launch.knot.KnotClient') {
        throw 'Metadata profil Fabric tidak sesuai versi yang diminta.'
    }
    $changes = @()
    foreach ($library in $fabricProfile.libraries) {
        if ($library.name -notmatch '^([a-zA-Z0-9_.-]+):([a-zA-Z0-9_.-]+):([a-zA-Z0-9_.+-]+)$') {
            throw "Format dependensi Fabric tidak didukung: $($library.name)"
        }
        $libraryPath = "$($Matches[1].Replace('.', '/'))/$($Matches[2])/$($Matches[3])/$($Matches[2])-$($Matches[3]).jar"
        $stagedLibrary = Join-Path $stageDirectory ([IO.Path]::GetFileName($libraryPath))
        $hash = ''
        if ($library.PSObject.Properties.Name -contains 'sha256') { $hash = $library.sha256 }
        Get-VerifiedDownload ($library.url.TrimEnd('/') + '/' + $libraryPath) $stagedLibrary $hash
        $changes += [pscustomobject] @{ RelativePath = "libraries/$libraryPath"; Action = 'Copy'; Source = $stagedLibrary }
    }
    $stagedProfile = Join-Path $stageDirectory 'version.json'
    Write-Json $stagedProfile $fabricProfile
    $changes += [pscustomobject] @{ RelativePath = "versions/$profileId/$profileId.json"; Action = 'Copy'; Source = $stagedProfile }
    $apiJar = Join-Path $stageDirectory $apiName
    Get-VerifiedDownload "https://maven.fabricmc.net/net/fabricmc/fabric-api/fabric-api/$apiVersion/$apiName" $apiJar
    $downloadedApi = Get-ModInfo $apiJar
    if ($downloadedApi.id -ne 'fabric-api' -or $downloadedApi.version -ne $apiVersion) { throw 'Paket Fabric API tidak sesuai.' }
    $stagedMod = Join-Path $stageDirectory $modName
    Copy-Item -LiteralPath $sourceJar -Destination $stagedMod
    foreach ($oldName in $oldModFiles) {
        if ($oldName -notin @($modName, $apiName)) {
            $changes += [pscustomobject] @{ RelativePath = "mods/$oldName"; Action = 'Remove'; Source = $null }
        }
    }
    $changes += [pscustomobject] @{ RelativePath = "mods/$modName"; Action = 'Copy'; Source = $stagedMod }
    $changes += [pscustomobject] @{ RelativePath = "mods/$apiName"; Action = 'Copy'; Source = $apiJar }
    foreach ($relativePath in $launcherPaths) {
        $document = $launcherDocuments[$relativePath]
        if ($document.PSObject.Properties.Name -notcontains 'profiles') { Set-JsonProperty $document 'profiles' ([pscustomobject] @{}) }
        if ($null -eq $document.profiles -or $document.profiles -isnot [pscustomobject]) { throw "Daftar profil launcher tidak valid: $relativePath" }
        $profileProperty = $document.profiles.PSObject.Properties[$launcherProfileKey]
        $profile = if ($null -ne $profileProperty) { $profileProperty.Value } else {
            [pscustomobject] @{ created = [DateTime]::UtcNow.ToString('o'); icon = 'Grass'; type = 'custom' }
        }
        if ($profile -isnot [pscustomobject]) { throw 'Profil Bow Pack yang tersimpan tidak valid.' }
        Set-JsonProperty $profile 'name' 'Bow Pack 26.3'
        Set-JsonProperty $profile 'lastVersionId' $profileId
        Set-JsonProperty $profile 'gameDir' $minecraftRoot
        Set-JsonProperty $document.profiles $launcherProfileKey $profile
        $stagedLauncher = Join-Path $stageDirectory $relativePath
        Write-Json $stagedLauncher $document
        $changes += [pscustomobject] @{ RelativePath = $relativePath; Action = 'Copy'; Source = $stagedLauncher }
    }
    Assert-MinecraftClosed
    foreach ($relativePath in $launcherPaths) {
        if ((Get-FileHash -LiteralPath (Join-Path $minecraftRoot $relativePath) -Algorithm SHA256).Hash -ne $launcherHashes[$relativePath]) {
            throw 'Konfigurasi launcher berubah selama persiapan. Tutup launcher lalu jalankan pemasang lagi.'
        }
    }
    $backup = Join-Path $minecraftRoot ('bow-pack-backups/' + (Get-Date -Format 'yyyyMMdd-HHmmss') + '-' + [Guid]::NewGuid().ToString('N').Substring(0, 6))
    Install-StagedFiles $minecraftRoot $backup $changes
    Write-Output 'Bow Pack terpasang. Buka Minecraft Launcher, pilih instalasi Bow Pack 26.3, lalu Play.'
    Write-Output "Cadangan: $backup"
}
finally {
    if ($null -ne $stageDirectory -and (Test-Path -LiteralPath $stageDirectory)) {
        $safeStage = Resolve-InstallPath ([IO.Path]::GetTempPath()) ([IO.Path]::GetFileName($stageDirectory))
        if ($safeStage -ne [IO.Path]::GetFullPath($stageDirectory)) { throw 'Lokasi folder sementara tidak sesuai.' }
        Remove-Item -LiteralPath $safeStage -Recurse -Force
    }
}
