"""Stage the pinned ARM64 builder for the opt-in PTrack Prova cotxe pilot."""

import argparse
import hashlib
import io
import json
from pathlib import Path, PurePosixPath
import subprocess
import sys
import tarfile
import zipfile


WHEEL = "pyvalhalla-3.9.1-cp312-abi3-manylinux_2_28_aarch64.whl"
SHA256 = {
    WHEEL: "6ebe5f9dd6ce3b16413f939a271d68e64a4addd44df8a802a8eda0b9ca0bf16f",
    "glibc-termux.deb": "5392d254e84c436e96783bcb6ffffe429d6b24c7ad00490f23f06f66c4769fb6",
    "libgcc-buster.deb": "2851ac25d12958586c035de5ec4f2fc17272dec48f776dd0dd24c62f62674fd9",
    "libstdc-buster.deb": "52cf36333a405867a079a695f6a37cb63558859d7d19cef40fc7d112c39fefd6",
}


def deb_libraries(package, files):
    """Read just the ARM64 ELF payloads needed by the builder."""
    offset = 8
    while offset < len(package):
        name = package[offset:offset + 16].strip()
        size = int(package[offset + 48:offset + 58])
        offset += 60
        if name.startswith(b"data.tar."):
            with tarfile.open(fileobj=io.BytesIO(package[offset:offset + size])) as tar:
                return {
                    alias: tar.extractfile(next(
                        member for member in tar.getmembers()
                        if member.isfile() and PurePosixPath(member.name).name == original
                    )).read()
                    for alias, original in files.items()
                }
        offset += size + size % 2
    raise ValueError("Missing Debian data archive")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("downloads", type=Path, help="Directory containing the four verified source packages")
    parser.add_argument("output", type=Path, help="Generated Android asset and JNI library directories")
    args = parser.parse_args()
    packages = {name: (args.downloads / name).read_bytes() for name in SHA256}
    for name, contents in packages.items():
        if hashlib.sha256(contents).hexdigest() != SHA256[name]:
            raise ValueError(f"Incorrect SHA-256 for {name}")

    native = args.output / "jniLibs/arm64-v8a"
    assets = args.output / "assets/ceuta-builder"
    native.mkdir(parents=True, exist_ok=True)
    assets.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(io.BytesIO(packages[WHEEL])) as wheel:
        (native / "libceuta_builder.so").write_bytes(wheel.read("valhalla/bin/valhalla_build_tiles"))
        libraries = {
            PurePosixPath(name).name: wheel.read(name)
            for name in wheel.namelist() if name.startswith("pyvalhalla.libs/lib")
        }
        config_script = wheel.read("valhalla/valhalla_build_config.py")
    config = subprocess.run([sys.executable, "-"], input=config_script, stdout=subprocess.PIPE, check=True).stdout
    (assets / "valhalla.json").write_bytes(config)

    # Termux's glibc uses Android-compatible implementations for Linux-only syscalls.
    loader = "ld-linux-aarch64.so.1"
    glibc = ("libc.so.6", "libdl.so.2", "libm.so.6", "libpthread.so.0", "libresolv.so.2", "librt.so.1", loader)
    libraries.update(deb_libraries(packages["glibc-termux.deb"], {name: name for name in glibc}))
    libraries.update(deb_libraries(packages["libgcc-buster.deb"], {"libgcc_s.so.1": "libgcc_s.so.1"}))
    libraries.update(deb_libraries(packages["libstdc-buster.deb"], {"libstdc++.so.6": "libstdc++.so.6.0.25"}))
    (native / "libceuta_loader.so").write_bytes(libraries.pop(loader))
    aliases = {loader: "libceuta_loader.so"}
    for index, (alias, contents) in enumerate(sorted(libraries.items())):
        name = f"libceuta_dep{index}.so"
        (native / name).write_bytes(contents)
        aliases[alias] = name
    (assets / "files.json").write_text(json.dumps(aliases, indent=2), encoding="utf-8")


if __name__ == "__main__":
    main()
