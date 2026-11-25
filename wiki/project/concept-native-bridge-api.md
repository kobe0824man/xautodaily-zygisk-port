---
title: The native bridge is never compiled by the source-level suite
type: concept
scope: project
---

`source/app/src/main/cpp/zygisk_hook.cpp` is C++. The source-level suite
(`source/zygisk/tests/verify_local_port.sh`) compiles the Java package, the vendored API 82
implementation and the unit tests with a plain JDK — it never compiles the native bridge, and the
sandbox has no NDK, CMake or Ninja to do so. Until the CI build actually runs, the C++ bridge is
unverified code.

That gap was real: the bridge passed the `jmethodID` of `XposedHooker.dispatch` to
`lsplant::Hook`, which expects a `java.lang.reflect.Method` *object*. In the NDK's `jni.h`,
`jmethodID` is `struct _jmethodID *` while `jobject` is `class _jobject *` — unrelated types, so
the call could not compile, and a permissive compiler would still have failed LSPlant's own
`java.lang.reflect.Executable` check at runtime. Nothing had caught it, because nothing had
compiled the file.

**How it is pinned now** — `source/zygisk/tests/verify_native_api.py` checks the bridge against
the pinned upstream headers:

- every `lsplant::` symbol it calls exists in lsplant-standalone 6.4;
- every `InitInfo` field it assigns exists in that header's `InitInfo`;
- every argument it passes to an LSPlant entry point has a declared type the header accepts —
  this is the check that catches the `jmethodID`/`jobject` confusion;
- every `Dobby*` symbol it calls exists in the pinned Dobby submodule's public header.

The check runs as step 7 of the source-level suite, so it runs in CI too. It is a type check, not
a compile: it catches API drift and argument-kind mistakes, not every C++ error. The authoritative
C++ compile is the Gradle build's `externalNativeBuild`, which only a real runner can perform —
see [[decision-ci-personal-repository]].

The other pins the bridge depends on are the API 82 JNI table
([[decision-jni-table-exact]]) and the vendored runtime ([[decision-api82-runtime]]).
