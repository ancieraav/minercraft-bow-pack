Set-StrictMode -Version Latest

function Assert-MinecraftClosed {
    $running = @(Get-CimInstance Win32_Process | Where-Object {
        $_.Name -match '^(Minecraft|MinecraftLauncher|Minecraft.Windows|MinecraftInstaller)\.exe$' -or
        ($_.Name -match '^javaw?\.exe$' -and $_.CommandLine -match 'net\.minecraft|KnotClient|--gameDir')
    })
    if ($running.Count -gt 0) {
        throw 'Tutup Minecraft dan Minecraft Launcher sepenuhnya, lalu jalankan pemasang lagi.'
    }
}

function Get-ModInfo([string] $Path) {
    $archive = [IO.Compression.ZipFile]::OpenRead($Path)
    try {
        $entry = $archive.GetEntry('fabric.mod.json')
        if ($null -eq $entry) { return $null }
        $reader = [IO.StreamReader]::new($entry.Open())
        try { return ($reader.ReadToEnd() | ConvertFrom-Json) }
        finally { $reader.Dispose() }
    }
    finally { $archive.Dispose() }
}

function Set-JsonProperty($Object, [string] $Name, $Value) {
    $Object | Add-Member -MemberType NoteProperty -Name $Name -Value $Value -Force
}

function Write-Json([string] $Path, $Value) {
    [IO.File]::WriteAllText($Path, ($Value | ConvertTo-Json -Depth 100), [Text.UTF8Encoding]::new($false))
}

function Get-VerifiedDownload([string] $Url, [string] $Destination, [string] $Sha256 = '') {
    $uri = [Uri] $Url
    if ($uri.Scheme -ne 'https' -or $uri.Host -ne 'maven.fabricmc.net') {
        throw "Sumber unduhan Fabric tidak dikenali: $Url"
    }
    if ([string]::IsNullOrWhiteSpace($Sha256)) {
        $response = Invoke-WebRequest -UseBasicParsing -Uri "$Url.sha256"
        $content = $response.Content
        if ($content -is [byte[]]) { $content = [Text.Encoding]::UTF8.GetString($content) }
        $Sha256 = ([string] $content).Trim()
    }
    if ($Sha256 -notmatch '^[a-fA-F0-9]{64}$') { throw "Checksum Fabric tidak valid: $Url" }
    Invoke-WebRequest -UseBasicParsing -Uri $Url -OutFile $Destination
    if ((Get-FileHash -LiteralPath $Destination -Algorithm SHA256).Hash -ine $Sha256) {
        throw "Unduhan tidak lolos pemeriksaan SHA-256: $Url"
    }
}

function Resolve-InstallPath([string] $Root, [string] $RelativePath) {
    $rootPrefix = [IO.Path]::GetFullPath($Root).TrimEnd('\', '/') + [IO.Path]::DirectorySeparatorChar
    $result = [IO.Path]::GetFullPath((Join-Path $Root $RelativePath))
    if (-not $result.StartsWith($rootPrefix, [StringComparison]::OrdinalIgnoreCase)) {
        throw "Lokasi berkas keluar dari folder Minecraft: $RelativePath"
    }
    return $result
}

function Copy-Atomic([string] $Source, [string] $Destination) {
    $null = [IO.Directory]::CreateDirectory([IO.Path]::GetDirectoryName($Destination))
    $temporary = $Destination + '.bowpack-' + [Guid]::NewGuid().ToString('N') + '.tmp'
    try {
        [IO.File]::Copy($Source, $temporary)
        if ([IO.File]::Exists($Destination)) { [IO.File]::Replace($temporary, $Destination, [NullString]::Value) }
        else { [IO.File]::Move($temporary, $Destination) }
    }
    finally {
        if ([IO.File]::Exists($temporary)) { Remove-Item -LiteralPath $temporary -Force }
    }
}

function Install-StagedFiles([string] $Root, [string] $Backup, [array] $Changes) {
    $manifest = @()
    foreach ($change in $Changes) {
        $target = Resolve-InstallPath $Root $change.RelativePath
        $existed = Test-Path -LiteralPath $target -PathType Leaf
        if ($existed) {
            $saved = Resolve-InstallPath $Backup ('files/' + $change.RelativePath)
            $null = [IO.Directory]::CreateDirectory([IO.Path]::GetDirectoryName($saved))
            Copy-Item -LiteralPath $target -Destination $saved
        }
        $manifest += [pscustomobject] @{ RelativePath = $change.RelativePath; Existed = $existed }
    }
    $null = [IO.Directory]::CreateDirectory($Backup)
    Write-Json (Join-Path $Backup 'manifest.json') $manifest
    $applied = [Collections.Generic.List[int]]::new()
    try {
        for ($index = 0; $index -lt $Changes.Count; $index++) {
            $change = $Changes[$index]
            $target = Resolve-InstallPath $Root $change.RelativePath
            $applied.Add($index)
            if ($change.Action -eq 'Remove') { Remove-Item -LiteralPath $target -Force }
            else {
                Copy-Atomic $change.Source $target
                if ((Get-FileHash -LiteralPath $change.Source -Algorithm SHA256).Hash -ne
                    (Get-FileHash -LiteralPath $target -Algorithm SHA256).Hash) {
                    throw "Verifikasi salinan gagal: $($change.RelativePath)"
                }
            }
        }
    }
    catch {
        $installError = $_
        $rollbackErrors = [Collections.Generic.List[string]]::new()
        for ($index = $applied.Count - 1; $index -ge 0; $index--) {
            $entry = $manifest[$applied[$index]]
            $target = Resolve-InstallPath $Root $entry.RelativePath
            try {
                if ($entry.Existed) { Copy-Atomic (Resolve-InstallPath $Backup ('files/' + $entry.RelativePath)) $target }
                elseif (Test-Path -LiteralPath $target -PathType Leaf) { Remove-Item -LiteralPath $target -Force }
            }
            catch { $rollbackErrors.Add($_.Exception.Message) }
        }
        if ($rollbackErrors.Count -gt 0) {
            throw "Pemasangan gagal: $installError. Pemulihan belum lengkap: $($rollbackErrors -join '; '). Cadangan: $Backup"
        }
        throw "Pemasangan gagal dan perubahan berkas sudah dipulihkan: $installError. Cadangan: $Backup"
    }
}
