---
title: Match the JNI table to the API 82 surface exactly
type: decision
scope: project
---

API 82's `XposedBridge` declares exactly ten native methods: `getStartClassName`, `getRuntime`,
`startsSystemServer`, `getXposedVersion`, `initNative`, `hookMethodNative`,
`invokeOriginalMethodNative`, `setObjectClassNative`, `dumpObjectNative`,
`cloneToSubclassNative`.

`RegisterNatives` rejects the whole table if any registered name or descriptor is absent, so the
table in `source/app/src/main/cpp/zygisk_hook.cpp` must be exactly this set with exactly these
descriptors. An earlier table registered six methods that API 82 does not declare
(`hadInitErrors`, `initXResourcesNative`, `removeFinalFlagNative`, `closeFilesBeforeForkNative`,
`reopenFilesAfterForkNative`, `invalidateCallersNative`) and omitted `initNative`; that would have
failed `JNI_OnLoad` on device.

**Decision**: the table is machine-checked in CI by `verify_xposed_api82.py` against the pinned
upstream source JAR, and the same check runs locally in `zygisk/tests/verify_local_port.sh`.
