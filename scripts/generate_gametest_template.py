#!/usr/bin/env python3
"""Writes an all-air GameTest structure template (gzipped NBT) of the given size.

Usage: generate_gametest_template.py <x> <y> <z> <output.nbt>
"""
from __future__ import annotations

import gzip
import struct
import sys
from pathlib import Path

DATA_VERSION = 3120  # same as the existing empty3x3x3 / empty7x3x7 templates; DFU upgrades it on load

TAG_END, TAG_INT, TAG_LIST, TAG_COMPOUND, TAG_STRING = 0, 3, 9, 10, 8


def _name(name: str) -> bytes:
    raw = name.encode("utf-8")
    return struct.pack(">H", len(raw)) + raw


def _empty_list(name: str) -> bytes:
    return bytes([TAG_LIST]) + _name(name) + bytes([TAG_COMPOUND]) + struct.pack(">i", 0)


def build_template(x: int, y: int, z: int) -> bytes:
    for axis, value in (("x", x), ("y", y), ("z", z)):
        if value < 1 or value > 48:
            raise ValueError(f"{axis} size must be between 1 and 48, got {value}")
    out = bytearray()
    out += bytes([TAG_COMPOUND]) + _name("")
    out += bytes([TAG_INT]) + _name("DataVersion") + struct.pack(">i", DATA_VERSION)
    out += bytes([TAG_LIST]) + _name("size") + bytes([TAG_INT]) + struct.pack(">i", 3) + struct.pack(">3i", x, y, z)
    out += _empty_list("blocks")
    out += _empty_list("entities")
    out += bytes([TAG_LIST]) + _name("palette") + bytes([TAG_COMPOUND]) + struct.pack(">i", 1)
    out += bytes([TAG_STRING]) + _name("Name") + _name("minecraft:air")
    out += bytes([TAG_END])
    out += bytes([TAG_END])
    return bytes(out)


def main(argv: list[str]) -> int:
    if len(argv) != 5:
        print(__doc__, file=sys.stderr)
        return 2
    try:
        x, y, z = (int(value) for value in argv[1:4])
        payload = build_template(x, y, z)
    except ValueError as error:
        print(f"error: {error}", file=sys.stderr)
        return 2
    target = Path(argv[4])
    try:
        target.parent.mkdir(parents=True, exist_ok=True)
        # mtime=0 keeps the output byte-identical between runs
        with target.open("wb") as raw, gzip.GzipFile(fileobj=raw, mode="wb", mtime=0) as gz:
            gz.write(payload)
    except OSError as error:
        print(f"error: cannot write {target}: {error}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
