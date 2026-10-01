param (
    [string]$OutputFile = "..\Ayushflix.wgt"
)

$PSScriptRoot = Split-Path -Parent -Path $MyInvocation.MyCommand.Definition
Set-Location $PSScriptRoot

Write-Host "Building Samsung Tizen Widget (.wgt)..." -ForegroundColor Cyan

$tempZip = Join-Path $env:TEMP "ayushflix_temp.zip"
if (Test-Path $tempZip) { Remove-Item $tempZip -Force }

# Files and folders to include
$itemsToPack = @(
    "config.xml",
    "index.html",
    "icon.png",
    "css",
    "js"
)

# Zip contents
Compress-Archive -Path $itemsToPack -DestinationPath $tempZip -Force

# Rename/Copy to destination with .wgt extension
$destination = Resolve-Path (Join-Path $PSScriptRoot $OutputFile) -ErrorAction SilentlyContinue
if (-not $destination) {
    $destination = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot $OutputFile))
}

Copy-Item $tempZip -Destination $destination -Force
Remove-Item $tempZip -Force

Write-Host "Successfully generated Tizen Widget package: $destination" -ForegroundColor Green
Write-Host "File size: $((Get-Item $destination).Length / 1KB) KB" -ForegroundColor Yellow
