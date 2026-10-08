"""Package Arduino CLI output with the USB flasher and SHA-256 checksums."""
import hashlib
import pathlib
import shutil
import sys
import zipfile

root = pathlib.Path(__file__).resolve().parents[1]
folder = pathlib.Path(sys.argv[1])
version = sys.argv[2]
required = ["clockinator.ino.bin", "clockinator.ino.bootloader.bin", "clockinator.ino.partitions.bin", "boot_app0.bin"]
for name in required:
    if not (folder / name).is_file():
        raise SystemExit(f"Missing firmware component: {name}")
shutil.copy(root / "scripts/flash-clock.ps1", folder / "flash-clock.ps1")
(folder / "README.txt").write_text(f"""Clockinator {version} - classic ESP32, 4 MB flash, No OTA (2MB APP/2MB SPIFFS).
This build is NOT for ESP32-S3/C3/C6 or a different screen/pinout.
Install Python, then: python -m pip install esptool==5.1.0
Close the Arduino serial monitor. Connect the ESP32 by USB.
Update an existing No OTA installation:
  powershell -ExecutionPolicy Bypass -File ./flash-clock.ps1 -Port COM5
First install or change from Arduino's default partition scheme:
  powershell -ExecutionPolicy Bypass -File ./flash-clock.ps1 -Port COM5 -InitialInstall
Use your actual COM port. Hold BOOT if connecting fails.
A partition change can invalidate LittleFS files: keep original WAVs and resend them.
No erase-all command is used. Application-only updates preserve NVS and LittleFS
when the existing partition scheme matches. The merged image is for blank devices:
writing its padded gaps can erase stored settings. Prefer the supplied script.
Linux/macOS update: python -m esptool --chip esp32 --port /dev/ttyUSB0 write-flash 0x10000 clockinator.ino.bin
""", encoding="utf-8")
archive = folder.parent / f"clockinator-esp32-{version}.zip"
with zipfile.ZipFile(archive, "w", zipfile.ZIP_DEFLATED) as output:
    names = required + ["README.txt", "flash-clock.ps1"]
    if (folder / "clockinator.ino.merged.bin").is_file(): names.append("clockinator.ino.merged.bin")
    for name in sorted(names): output.write(folder / name, name)
assets = sorted(p for p in folder.parent.iterdir() if p.is_file() and p.name != "SHA256SUMS.txt")
(folder.parent / "SHA256SUMS.txt").write_text("".join(
    f"{hashlib.sha256(p.read_bytes()).hexdigest()}  {p.name}\n" for p in assets), encoding="utf-8")
