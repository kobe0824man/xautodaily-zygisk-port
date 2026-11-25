#!/usr/bin/env python3
"""Verify the pinned OpenSSL 1.1.1t static archives and their target ABIs."""
from __future__ import annotations

import hashlib
import struct
from pathlib import Path

EXPECTED = {
    "arm64-v8a/libcrypto.a": ("a85f2612ab8cf3d1617fca10a439753939239357", 2, 183),
    "arm64-v8a/libssl.a": ("411607b9c1f00a7fd6c5e1b264b5b6ec328b20ee", 2, 183),
    "armeabi-v7a/libcrypto.a": ("1ea56d45318dd63486929e1cbffc1f367ef9aa32", 1, 40),
    "armeabi-v7a/libssl.a": ("00509224b33a1826eda6bce71512a59666354578", 1, 40),
}

def first_elf_member(data: bytes) -> bytes:
    if not data.startswith(b"!<arch>\n"):
        raise ValueError("not a Unix ar archive")
    offset = 8
    while offset + 60 <= len(data):
        header = data[offset:offset + 60]
        if header[58:60] != bytes([96, 10]):
            raise ValueError("invalid ar member header")
        size = int(header[48:58].decode("ascii").strip())
        body = offset + 60
        member = data[body:body + size]
        if member.startswith(bytes([127]) + b"ELF"):
            return member
        offset = body + size + (size & 1)
    raise ValueError("no ELF object member found in archive")

root = Path(__file__).resolve().parents[2] / "openssl" / "lib"
for relative, (expected_git_sha, expected_class, expected_machine) in EXPECTED.items():
    path = root / relative
    data = path.read_bytes()
    git_header = b"blob " + str(len(data)).encode("ascii") + bytes([0])
    git_sha = hashlib.sha1(git_header + data).hexdigest()
    if git_sha != expected_git_sha:
        raise SystemExit(f"{path}: Git blob SHA mismatch: {git_sha} != {expected_git_sha}")
    elf = first_elf_member(data)
    elf_class = elf[4]
    endian = "<" if elf[5] == 1 else ">"
    machine = struct.unpack_from(endian + "H", elf, 18)[0]
    if (elf_class, machine) != (expected_class, expected_machine):
        raise SystemExit(
            f"{path}: wrong ELF target class={elf_class} machine={machine}; "
            f"expected class={expected_class} machine={expected_machine}"
        )
    print(f"PASS {relative}: pinned Git blob {git_sha}, ELF class={elf_class}, machine={machine}")
