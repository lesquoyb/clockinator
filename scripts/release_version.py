"""Validate/increment the shared release version before any git push."""
import argparse
import pathlib
import re

ROOT = pathlib.Path(__file__).resolve().parents[1]

def update(version, root=ROOT):
    if not re.fullmatch(r"(?:0|[1-9]\d*)\.(?:0|[1-9]\d*)\.(?:0|[1-9]\d*)", version):
        raise ValueError("Expected MAJOR.MINOR.PATCH, e.g. 1.0.1")
    props = dict(line.split("=", 1) for line in (root / "version.properties").read_text().splitlines() if "=" in line)
    if tuple(map(int, version.split("."))) <= tuple(map(int, props["versionName"].split("."))):
        raise ValueError("The version must be greater than the current version")
    code = int(props["versionCode"]) + 1
    if code > 2100000000:
        raise ValueError("Android versionCode limit reached")
    (root / "version.properties").write_text(f"versionName={version}\nversionCode={code}\n", encoding="utf-8")
    (root / "esp32/clockinator/Version.h").write_text(
        '#pragma once\n// Updated with version.properties by scripts/release_version.py.\n'
        f'#define CLOCKINATOR_VERSION "{version}"\n', encoding="utf-8")

if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("version")
    args = parser.parse_args()
    try:
        update(args.version)
    except ValueError as error:
        parser.error(str(error))
