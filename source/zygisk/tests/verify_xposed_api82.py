#!/usr/bin/env python3
"""Verify the JNI registration table against the pinned Xposed API 82 source jar."""
from __future__ import annotations

import re
import sys
import zipfile
from pathlib import Path

if len(sys.argv) != 3:
    raise SystemExit("usage: verify_xposed_api82.py <api-82-sources.jar> <zygisk_hook.cpp>")

with zipfile.ZipFile(Path(sys.argv[1])) as archive:
    source = archive.read("de/robv/android/xposed/XposedBridge.java").decode("utf-8")
cpp = Path(sys.argv[2]).read_text(encoding="utf-8")
# Strip comments before parsing so documentation examples cannot be mistaken for declarations.
source = re.sub(r"/\*.*?\*/|//[^\n]*", " ", source, flags=re.DOTALL)

primitive = {
    "void": "V", "boolean": "Z", "byte": "B", "char": "C",
    "short": "S", "int": "I", "long": "J", "float": "F", "double": "D",
}
objects = {
    "String": "Ljava/lang/String;",
    "Member": "Ljava/lang/reflect/Member;",
    "Class": "Ljava/lang/Class;",
    "Object": "Ljava/lang/Object;",
}

def descriptor(java_type: str) -> str:
    value = java_type.strip().replace("final ", "").replace("<?>", "")
    dimensions = 0
    while value.endswith("[]"):
        dimensions += 1
        value = value[:-2].strip()
    base = primitive.get(value) or objects.get(value)
    if base is None:
        raise ValueError(f"unsupported API-82 native type: {java_type!r}")
    return "[" * dimensions + base

native_decl = re.compile(
    r"\bnative\s+(?:(?:static|synchronized)\s+)*(\w+)\s+(\w+)\s*\(([^)]*)\)\s*(?:throws\s+[^;]+)?;",
    re.DOTALL,
)
expected: dict[str, str] = {}
for match in native_decl.finditer(source):
    return_type, name, parameters = match.groups()
    params = []
    if parameters.strip():
        for parameter in parameters.split(","):
            parts = parameter.strip().split()
            if len(parts) < 2:
                raise ValueError(f"cannot parse API-82 parameter: {parameter!r}")
            params.append(descriptor(" ".join(parts[:-1])))
    expected[name] = "(" + "".join(params) + ")" + descriptor(return_type)

registered = dict(re.findall(
    r'\{const_cast<char \*>\("([^"]+)"\),\s*'
    r'const_cast<char \*>\("([^"]+)"',
    cpp,
))
if expected != registered:
    missing = {key: value for key, value in expected.items() if key not in registered}
    extra = {key: value for key, value in registered.items() if key not in expected}
    wrong = {
        key: (expected[key], registered[key])
        for key in expected.keys() & registered.keys()
        if expected[key] != registered[key]
    }
    print(f"API-82 native declarations: {expected}", file=sys.stderr)
    print(f"JNI registration table:     {registered}", file=sys.stderr)
    raise SystemExit(f"JNI/API mismatch: missing={missing}, extra={extra}, wrong_descriptors={wrong}")

print(f"PASS: all {len(expected)} XposedBridge API-82 native methods and JNI descriptors match")
