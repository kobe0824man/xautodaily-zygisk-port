---
type: architecture-decision-record
agent: architect
title: "ADR: Vendor the Xposed API 82 runtime into the Zygisk payload"
slug: adr-vendor-the-xposed-api-82-runtime-into-the-zygisk-payload
status: proposed
createdAt: 2026-11-25T03:37:13.847Z
updatedAt: 2026-11-25T03:37:13.847Z
tags: [adr]
---

## Context

The Zygisk payload runs inside the target app process with no LSPosed framework, so the classes packaged in the APK are the implementation. The published `de.robv.android.xposed:api:82` artifact is a compile-time stub whose method bodies all throw `RuntimeException("Stub!")` and which declares only `getXposedVersion` as native. Packaging it would have made every `XposedBridge.hookMethod`, `XposedBridge.invokeOriginalMethod` and `XposedHelpers.findAndHookMethod` call throw at runtime, and the JNI bridge could not have registered its natives because the stub class does not declare them. The JNI registration table also had to match the API 82 native surface exactly, because `RegisterNatives` rejects an entire table when any registered name or descriptor is absent.

## Decision

Package the upstream API 82 implementation in the payload instead of the published stub: a new Gradle module `:xposed-api82` holds the sources vendored from the pinned `api-82-sources.jar` (Apache-2.0) and is used by `-PzygiskPort=true` builds only; `:xposed-api82-stubs` holds compile-only stubs for the hidden framework APIs the upstream sources reference and is consumed with `compileOnly` so it never reaches the APK. The JNI table in `zygisk_hook.cpp` is reduced to the ten native methods API 82 actually declares, and `source/zygisk/tests/verify_xposed_api82.py` checks the table and the vendored tree against the pinned upstream sources in CI and locally.

## Alternatives Considered

### Keep consuming the published api-82 artifact
- Pros: No vendored code to maintain, Upstream API updates arrive with the dependency
- Cons: Every API call throws RuntimeException("Stub!") at runtime, RegisterNatives cannot bind the native entry points

### Write a minimal API 82 implementation from scratch
- Pros: Full control over the surface
- Cons: Thousands of lines to reimplement and no upstream parity, No battle-tested callback ordering or exception semantics

## Consequences

The payload now carries a working legacy API runtime, so the port's hook path can work without LSPosed. The cost is ownership: API 82 upstream changes must be re-vendored deliberately, and the vendored tree carries two documented adaptations for hidden framework APIs (public `Resources` drawable overloads, hidden `AssetManager#addAssetPath`). The machine check in `verify_xposed_api82.py` keeps the vendored copy honest against the pinned upstream source JAR. Normal (non-port) builds keep the published artifact, so upstream behaviour is unchanged.
