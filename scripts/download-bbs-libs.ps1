<#
.SYNOPSIS
    Downloads the BBS CML Edition mod jars required to build this addon.

.DESCRIPTION
    The BBS CML Edition jars are not committed to the repository (they are large
    third-party binaries). This script fetches the 2.0-beta-1 jars for both
    supported Minecraft versions from Modrinth into the libs/ folder.

.EXAMPLE
    pwsh ./scripts/download-bbs-libs.ps1
#>

$ErrorActionPreference = "Stop"
$ProgressPreference = "SilentlyContinue"

$root = Split-Path -Parent $PSScriptRoot
$libs = Join-Path $root "libs"
New-Item -ItemType Directory -Force -Path $libs | Out-Null

$files = @(
    @{
        Name = "bbs-cml-edition-2.0-beta-1-1.21.4.jar"
        Url  = "https://cdn.modrinth.com/data/orQP37wm/versions/6B7ILS7p/bbs-cml-edition-2.0-beta-1-1.21.4.jar"
    },
    @{
        Name = "bbs-cml-edition-2.0-beta-1-1.21.1.jar"
        Url  = "https://cdn.modrinth.com/data/orQP37wm/versions/bEjsC5Ok/bbs-cml-edition-2.0-beta-1-1.21.1.jar"
    }
)

foreach ($f in $files) {
    $out = Join-Path $libs $f.Name
    if (Test-Path $out) {
        Write-Host "Already present: $($f.Name)"
        continue
    }
    Write-Host "Downloading $($f.Name)..."
    Invoke-WebRequest -Uri $f.Url -OutFile $out -TimeoutSec 300
    Write-Host "  -> $out"
}

Write-Host "Done."
