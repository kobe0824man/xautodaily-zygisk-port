---
title: Package a working API 82 runtime, not the published stub
type: decision
scope: project
---

The published `de.robv.android.xposed:api:82` artifact (`https://api.xposed.info`) is a
**compile-time stub**: every method body is `throw new RuntimeException("Stub!")` and only
`getXposedVersion` is left as a native declaration. LSPosed hides that stub behind the
framework's own `de.robv.android.xposed.*` classes, so modules never notice. The Zygisk payload
has no framework: the packaged classes *are* the implementation.

**Decision**: vendor the upstream API 82 implementation as the Gradle module `:xposed-api82`
(sources from the pinned `api-82-sources.jar`, Apache-2.0) and use it for `-PzygiskPort=true`
builds only. Normal upstream builds keep the published artifact.

**Why**: with the stub packaged, every `XposedBridge.hookMethod`, `XposedBridge.invokeOriginalMethod`
and `XposedHelpers.findAndHookMethod` call throws at runtime, and the JNI bridge cannot register
natives against a class that does not declare them.

**How it is kept honest**: `source/zygisk/tests/verify_xposed_api82.py` compares the vendored tree
with the pinned upstream source JAR byte for byte, allowing only the two documented adaptations
(hidden `Resources` overloads and the hidden `AssetManager#addAssetPath`), and checks that the
native declarations still match. `source/xposed-api82-stubs` supplies compile-only stubs for the
hidden framework classes and is consumed with `compileOnly`, so it never reaches the APK.

**Consequences**: the port now owns an API runtime; upstream API 82 changes must be re-vendored
deliberately. `XResources`/`XModuleResources` substitution stays unimplemented, as before.
