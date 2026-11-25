---
title: The CI build contract the workflow and the Gradle build must agree on
type: concept
scope: project
---

The GitHub Actions workflow and the Gradle build are two independent declarations of the same
facts. Nothing type-checks them against each other, so a change on either side can silently
break the other. These are the couplings, verified before the first real runner was ever
allocated (the build had never executed end to end before this).

**Toolchain versions** — declared in `source/gradle/libs.versions.toml`, installed by the
workflow's `sdkmanager` line:

| Fact | Value | Where it is declared |
| --- | --- | --- |
| `compileSdk` | 36 | `libs.versions.toml` / `platforms;android-36` |
| `buildToolsVersion` | 36.0.0 | `libs.versions.toml` / `build-tools;36.0.0` |
| `ndkVersion` | 27.0.12077973 | `libs.versions.toml` / `ndk;27.0.12077973` |
| CMake | 3.22.1 | AGP's default when `externalNativeBuild.cmake` sets no `version` / `cmake;3.22.1` |
| JDK | 21 | `jvm-target = 21`, `actions/setup-java` |
| Gradle | 8.14 | `source/gradle/wrapper/gradle-wrapper.properties` |
| AGP | 8.9.2 | `libs.versions.toml` (needs Gradle >= 8.11.1) |

`android.suppressUnsupportedCompileSdk=36` in `source/gradle.properties` is what keeps the
"compileSdk 36 has not been tested with this AGP" notice from becoming a build failure.

**Artifact paths** — the workflow hashes and verifies
`source/app/build/outputs/apk/debug/app-debug.apk`. That path is only correct because the app
module declares **no product flavours**; adding one moves the APK to
`apk/<flavor>/debug/app-<flavor>-debug.apk` and silently breaks the payload path, the ELF
verification step and `build-zygisk.sh` at the same time.

**Native libraries in the APK** — the packager extracts three `.so` files per ABI out of the
built APK, so each has to actually be in it:

- `libxautodaily_hook.so` — a CMake target of the app module itself.
- `liblsplant.so` — a Prefab `.so` from `org.lsposed.lsplant:lsplant-standalone:6.4`.
- `libdobby.so` — Dobby is added with `add_library(dobby SHARED ...)`; a static Dobby would
  silently remove it from the APK and break the packager's "missing native hook library" check.

**The loader entry point** — `REGISTER_ZYGISK_MODULE` expands to a plain
`void zygisk_module_entry(...)`. It only becomes a loadable Zygisk module because the vendored
`source/zygisk/module/jni/zygisk.hpp` declares it *earlier* inside an `extern "C"` block with
`[[gnu::visibility("default")]]`. That declaration is what gives the symbol C linkage and
default visibility; the `-fvisibility=hidden` compile flag in the loader's CMake target does not
override it, because an explicit visibility attribute wins over the flag. Remove or move that
declaration and the module stops loading at runtime, and `nm -D` in the workflow's ELF
verification step stops finding the symbol.

**The Dobby submodule is mandatory** — the app's `CMakeLists.txt` does
`add_subdirectory(../../../../zygisk/third_party/Dobby dobby_build)`. A checkout without
`submodules: recursive` cannot configure the native build at all.

See [[concept-port-validation]] for what each of these checks is allowed to prove, and
[[decision-ci-personal-repository]] for where the workflow runs.
