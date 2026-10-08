param(
    [Parameter(Mandatory = $true)][string]$Port,
    [switch]$InitialInstall,
    [string]$Python = "python"
)
$ErrorActionPreference = "Stop"
$files = @("clockinator.ino.bin")
if ($InitialInstall) { $files += @("clockinator.ino.bootloader.bin", "clockinator.ino.partitions.bin", "boot_app0.bin") }
foreach ($file in $files) {
    if (!(Test-Path -LiteralPath (Join-Path $PSScriptRoot $file) -PathType Leaf)) { throw "Missing firmware file: $file" }
}
$arguments = @("-m", "esptool", "--chip", "esp32", "--port", $Port, "--baud", "460800", "write-flash")
if ($InitialInstall) {
    # Individual writes preserve NVS/LittleFS instead of filling a merged image's gaps.
    $arguments += @("0x1000", (Join-Path $PSScriptRoot "clockinator.ino.bootloader.bin"),
        "0x8000", (Join-Path $PSScriptRoot "clockinator.ino.partitions.bin"),
        "0xe000", (Join-Path $PSScriptRoot "boot_app0.bin"))
}
$arguments += @("0x10000", (Join-Path $PSScriptRoot "clockinator.ino.bin"))
& $Python @arguments
if ($LASTEXITCODE -ne 0) { throw "Flashing failed (exit $LASTEXITCODE)." }
