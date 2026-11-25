#!/usr/bin/env python3
"""Verify the JNI registration table and the vendored API 82 sources against the pinned upstream.

Usage:
    verify_xposed_api82.py <api-82-sources.jar|dir> <zygisk_hook.cpp> [--vendored <dir>]

The first argument is the pinned upstream API 82 sources (the published source JAR, or an
unpacked directory). The second is the JNI bridge that registers the native entry points.

With --vendored the vendored copy is checked against the pinned sources: every file must be
byte-identical except for the documented local adaptations, and the native declarations must
match exactly.
"""
from __future__ import annotations

import re
import sys
import zipfile
from pathlib import Path

PRIMITIVE = {
    "void": "V", "boolean": "Z", "byte": "B", "char": "C",
    "short": "S", "int": "I", "long": "J", "float": "F", "double": "D",
}
OBJECTS = {
    "String": "Ljava/lang/String;",
    "Member": "Ljava/lang/reflect/Member;",
    "Class": "Ljava/lang/Class;",
    "Object": "Ljava/lang/Object;",
}

# Vendored files that carry documented local adaptations (see xposed-api82/PROVENANCE.md).
ALLOWED_DIFFERENCES = {
    "android/content/res/XResources.java",
    "android/content/res/XModuleResources.java",
}

NATIVE_DECL = re.compile(
    r"\bnative\s+(?:(?:static|synchronized)\s+)*(\w+)\s+(\w+)\s*\(([^)]*)\)\s*(?:throws\s+[^;]+)?;",
    re.DOTALL,
)
REGISTERED_ENTRY = re.compile(
    r'\{const_cast<char \*>\("([^"]+)"\),\s*const_cast<char \*>\("([^"]+)"',
)


def descriptor(java_type: str) -> str:
    value = java_type.strip().replace("final ", "").replace("<?>", "")
    dimensions = 0
    while value.endswith("[]"):
        dimensions += 1
        value = value[:-2].strip()
    base = PRIMITIVE.get(value) or OBJECTS.get(value)
    if base is None:
        raise ValueError(f"unsupported API-82 native type: {java_type!r}")
    return "[" * dimensions + base


def natives_of(source: str, strict: bool = True) -> dict[str, str]:
    """Return {method name: JNI descriptor} for every native declaration in the source.

    With strict=False, declarations whose types cannot be mapped to a JNI descriptor are skipped
    instead of raising. That is used for the resource classes, whose native methods return
    framework types that are irrelevant to the registration table.
    """
    # Strip comments so documentation examples cannot be mistaken for declarations.
    stripped = re.sub(r"/\*.*?\*/|//[^\n]*", " ", source, flags=re.DOTALL)
    natives: dict[str, str] = {}
    for match in NATIVE_DECL.finditer(stripped):
        return_type, name, parameters = match.groups()
        params = []
        try:
            if parameters.strip():
                for parameter in parameters.split(","):
                    parts = parameter.strip().split()
                    if len(parts) < 2:
                        raise ValueError(f"cannot parse API-82 parameter: {parameter!r}")
                    params.append(descriptor(" ".join(parts[:-1])))
            natives[name] = "(" + "".join(params) + ")" + descriptor(return_type)
        except ValueError:
            if strict:
                raise
    return natives


def read_reference(reference: Path) -> dict[str, str]:
    """Map of relative source path -> source text for the pinned upstream sources."""
    if reference.is_dir():
        return {
            str(path.relative_to(reference)): path.read_text(encoding="utf-8")
            for path in sorted(reference.rglob("*.java"))
        }
    with zipfile.ZipFile(reference) as archive:
        return {
            name: archive.read(name).decode("utf-8")
            for name in sorted(archive.namelist())
            if name.endswith(".java")
        }


def check_vendored(reference: dict[str, str], vendored: Path) -> None:
    vendored_files = {
        str(path.relative_to(vendored)): path.read_text(encoding="utf-8")
        for path in sorted(vendored.rglob("*.java"))
    }
    missing = sorted(set(reference) - set(vendored_files))
    extra = sorted(set(vendored_files) - set(reference))
    if missing or extra:
        raise SystemExit(f"vendored API 82 tree differs from upstream: missing={missing}, extra={extra}")

    unexpected: list[str] = []
    for name, text in vendored_files.items():
        if text == reference[name]:
            continue
        if name in ALLOWED_DIFFERENCES:
            continue
        unexpected.append(name)
    if unexpected:
        raise SystemExit(
            "vendored API 82 sources were modified without being documented: "
            f"{sorted(unexpected)} (allowed: {sorted(ALLOWED_DIFFERENCES)})"
        )

    reference_natives = natives_of(reference["de/robv/android/xposed/XposedBridge.java"])
    if natives_of(vendored_files["de/robv/android/xposed/XposedBridge.java"]) != reference_natives:
        raise SystemExit("vendored XposedBridge.java no longer declares the upstream native methods")
    for name in sorted(ALLOWED_DIFFERENCES):
        if name not in vendored_files:
            continue
        if natives_of(vendored_files[name], strict=False) != natives_of(reference[name], strict=False):
            raise SystemExit(f"vendored {name} no longer declares the upstream native methods")
    print(f"PASS: vendored API 82 tree matches upstream ({len(vendored_files)} files, "
          f"{len(ALLOWED_DIFFERENCES)} documented adaptations)")


def main() -> None:
    arguments = sys.argv[1:]
    vendored: Path | None = None
    if "--vendored" in arguments:
        index = arguments.index("--vendored")
        if index + 1 >= len(arguments):
            raise SystemExit("--vendored needs a directory")
        vendored = Path(arguments[index + 1])
        arguments = arguments[:index] + arguments[index + 2:]
    if len(arguments) != 2:
        raise SystemExit(
            "usage: verify_xposed_api82.py <api-82-sources.jar|dir> <zygisk_hook.cpp> "
            "[--vendored <dir>]"
        )

    reference_path = Path(arguments[0])
    cpp = Path(arguments[1]).read_text(encoding="utf-8")
    reference = read_reference(reference_path)

    expected = natives_of(reference["de/robv/android/xposed/XposedBridge.java"])
    registered = dict(REGISTERED_ENTRY.findall(cpp))

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
        raise SystemExit(
            f"JNI/API mismatch: missing={missing}, extra={extra}, wrong_descriptors={wrong}"
        )
    print(f"PASS: all {len(expected)} XposedBridge API-82 native methods and JNI descriptors match")

    if vendored is not None:
        check_vendored(reference, vendored)


if __name__ == "__main__":
    main()
