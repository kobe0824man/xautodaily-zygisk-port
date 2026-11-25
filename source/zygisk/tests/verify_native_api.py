#!/usr/bin/env python3
"""Verify the Zygisk native bridge against the pinned LSPlant and Dobby headers.

The bridge (`source/app/src/main/cpp/zygisk_hook.cpp`) is C++ and is never compiled by the
source-level suite, so an API mismatch in it would only surface as a CI build failure or,
worse, as a runtime crash inside a hooked app. This check pins the API surface the bridge
uses:

  * every `lsplant::` symbol it calls must exist in the pinned lsplant-standalone 6.4 header;
  * every `InitInfo` field it assigns must exist in that header's `InitInfo` struct;
  * every argument it passes to an LSPlant entry point must have a declared type the header
    accepts (LSPlant's `Hook` takes the callback as a `java.lang.reflect.Method` object, not
    as a `jmethodID`, and the two are unrelated C++ types);
  * every `Dobby*` symbol it calls must exist in the pinned Dobby submodule's public header.
"""
from __future__ import annotations

import re
import sys
import urllib.request
import zipfile
from pathlib import Path

LSPLANT_AAR = (
    "https://repo1.maven.org/maven2/org/lsposed/lsplant/lsplant-standalone/6.4/"
    "lsplant-standalone-6.4.aar"
)
LSPLANT_HEADER = "prefab/modules/lsplant/include/lsplant.hpp"

# Declared types that satisfy a header parameter type.
ACCEPTED = {
    "jobject": {"jobject", "jclass"},
    "jmethodID": {"jmethodID"},
    "jclass": {"jclass"},
    "const InitInfo &": {"lsplant::InitInfo", "InitInfo"},
    "InitInfo": {"lsplant::InitInfo", "InitInfo"},
    "JNIEnv *": {"JNIEnv *", "JNIEnv*"},
    "void *": {"void *", "void*"},
}

CPP_DECL = re.compile(
    r"\b(?:const\s+)?(jmethodID|jclass|jobject|lsplant::InitInfo|InitInfo|int|bool|void\s*\*)\s*"
    r"(?:\*\s*)?([A-Za-z_][A-Za-z0-9_]*)\s*[=;,)]"
)


def fail(message: str) -> "None":
    print(f"FAIL {message}")
    return None


def fetch_lsplant_header(cache_dir: Path) -> str:
    aar = cache_dir / "lsplant-standalone-6.4.aar"
    if not aar.exists():
        print(f"Fetching {aar}")
        with urllib.request.urlopen(LSPLANT_AAR, timeout=120) as response:
            aar.write_bytes(response.read())
    with zipfile.ZipFile(aar) as archive:
        return archive.read(LSPLANT_HEADER).decode("utf-8")


def parse_lsplant(header: str) -> tuple[dict[str, list[tuple[str, str]]], set[str]]:
    """Return the public functions of namespace lsplant and the InitInfo field names."""
    # C++ attributes such as [[nodiscard, ...]] sit between the documentation and the
    # declaration; remove them so the declaration starts at the return type.
    header = re.sub(r"\[\[[^\]]*\]\]", " ", header)

    functions: dict[str, list[tuple[str, str]]] = {}
    for match in re.finditer(
        r"(?:^|;|\})\s*(?:inline\s+)?([A-Za-z_][A-Za-z0-9_:<>* ]*?)\s+([A-Za-z_][A-Za-z0-9_]*)\s*\(",
        header,
        re.MULTILINE,
    ):
        name = match.group(2)
        start = match.end() - 1
        depth, index = 0, start
        while index < len(header):
            if header[index] == "(":
                depth += 1
            elif header[index] == ")":
                depth -= 1
                if depth == 0:
                    break
            index += 1
        parameters = header[start + 1 : index]
        parsed: list[tuple[str, str]] = []
        for parameter in split_arguments(parameters):
            parameter = parameter.strip()
            if not parameter or parameter == "void":
                continue
            words = re.findall(r"[A-Za-z_][A-Za-z0-9_:]*|&|\*", parameter)
            if len(words) < 2:
                continue
            parameter_name = words[-1]
            parameter_type = " ".join(words[:-1])
            parsed.append((re.sub(r"\s+", " ", parameter_type).strip(), parameter_name))
        functions[name] = parsed

    fields: set[str] = set()
    struct = re.search(r"struct InitInfo\s*\{(.*?)\n\};", header, re.DOTALL)
    if struct:
        fields = set(re.findall(r"^\s*(?:[A-Za-z_][A-Za-z0-9_:<>* ]*?)\s+([A-Za-z_][A-Za-z0-9_]*)\s*(?:=|;)",
                                struct.group(1), re.MULTILINE))
    return functions, fields


def split_arguments(text: str) -> list[str]:
    arguments, depth, current = [], 0, ""
    for character in text:
        if character == "," and depth == 0:
            arguments.append(current.strip())
            current = ""
            continue
        if character in "(<":
            depth += 1
        elif character in ")>":
            depth -= 1
        current += character
    if current.strip():
        arguments.append(current.strip())
    return arguments


def main() -> int:
    root = Path(__file__).resolve().parents[2]
    bridge = root / "app/src/main/cpp/zygisk_hook.cpp"
    dobby_header = root / "zygisk/third_party/Dobby/include/dobby.h"
    cache_dir = Path(sys.argv[1]) if len(sys.argv) > 1 else Path("/tmp")
    cache_dir.mkdir(parents=True, exist_ok=True)

    source = bridge.read_text()
    errors: list[str] = []

    header = fetch_lsplant_header(cache_dir)
    functions, fields = parse_lsplant(header)

    for symbol in sorted(set(re.findall(r"\blsplant::([A-Za-z_][A-Za-z0-9_]*)", source))):
        if symbol == "InitInfo":
            continue
        if symbol not in functions:
            errors.append(f"{bridge.name}: lsplant::{symbol} is not declared by lsplant-standalone 6.4")

    assigned = set(re.findall(r"\binfo\.([A-Za-z_][A-Za-z0-9_]*)\s*=", source))
    for field in sorted(assigned):
        if field not in fields:
            errors.append(f"{bridge.name}: InitInfo has no field '{field}' in lsplant-standalone 6.4")

    # Argument types at LSPlant call sites.
    declarations: dict[str, str] = {}
    for declared_type, name in CPP_DECL.findall(source):
        declarations.setdefault(name, re.sub(r"\s+", " ", declared_type).strip())

    for call in re.finditer(r"\blsplant::([A-Za-z_][A-Za-z0-9_]*)\s*\(([^;]*?)\)\s*;", source, re.DOTALL):
        name, argument_text = call.group(1), call.group(2)
        if name not in functions:
            continue
        parameters = functions[name]
        arguments = split_arguments(argument_text)
        if len(arguments) != len(parameters):
            errors.append(
                f"{bridge.name}: lsplant::{name} takes {len(parameters)} parameters, "
                f"the call site passes {len(arguments)}"
            )
            continue
        for (expected, _), argument in zip(parameters, arguments):
            match = re.fullmatch(r"[A-Za-z_][A-Za-z0-9_]*", argument)
            if not match:
                continue
            actual = declarations.get(argument)
            if actual is None:
                continue
            accepted = ACCEPTED.get(expected.replace(" &", " &").strip())
            if accepted is None:
                continue
            normalised = re.sub(r"\s+", " ", actual).strip()
            if normalised not in accepted:
                errors.append(
                    f"{bridge.name}: lsplant::{name} expects '{expected}' but '{argument}' is "
                    f"declared '{normalised}'"
                )

    dobby_source = dobby_header.read_text()
    for symbol in sorted(set(re.findall(r"\b(Dobby[A-Za-z_]*)\s*\(", source))):
        if not re.search(rf"\b{symbol}\s*\(", dobby_source):
            errors.append(f"{bridge.name}: {symbol} is not declared by the pinned Dobby header")

    if errors:
        for error in errors:
            print(f"FAIL {error}")
        print(f"{len(errors)} native API error(s)")
        return 1

    print(
        f"PASS native API: {len(functions)} lsplant entry points checked, "
        f"{len(fields)} InitInfo fields, LSPlant calls type-checked, Dobby symbols present"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
