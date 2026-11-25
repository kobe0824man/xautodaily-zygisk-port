# Vendored Xposed API 82 runtime

`src/main/java` contains the upstream Xposed API 82 implementation, extracted from the pinned
source artifact:

- Artifact: `de.robv.android.xposed:api:82`, source JAR `api-82-sources.jar`
- Origin: `https://api.xposed.info/de/robv/android/xposed/api/82/api-82-sources.jar`
- License: Apache-2.0 (same license as the published artifact)

## Why the sources are vendored

The published binary artifact `api-82.jar` is a **compile-time stub**: every method body is
`throw new RuntimeException("Stub!")`, and only `getXposedVersion` is left as a native
declaration. LSPosed hides that stub behind the framework's own `de.robv.android.xposed.*`
classes, so a module that only compiles against it never notices. The Zygisk payload has no
framework: the classes packaged in the APK *are* the implementation, so with the stub every
`XposedBridge.hookMethod(...)`, `XposedBridge.invokeOriginalMethod(...)` and
`XposedHelpers.findAndHookMethod(...)` call would throw `RuntimeException("Stub!")` at runtime,
and the JNI bridge could not register its natives because the stub class does not declare them.

Vendoring the upstream implementation gives the payload a working API layer whose native
declarations match the JNI registration table in `app/src/main/cpp/zygisk_hook.cpp`.

## Local changes to the upstream sources

The upstream sources are compiled against a full framework JAR with hidden APIs. The public
`android.jar` does not expose them, so the vendored copy carries four minimal adaptations, all
of which keep runtime behaviour intact:

| File | Change | Reason |
|---|---|---|
| `android/content/res/XResources.java` | dropped `@Override` on `getDrawable(int, Theme, boolean)` and `getDrawableForDensity(int, int, Theme, boolean)`; the `super` calls use the public two/three-argument overloads | the hidden four-argument `Resources` overloads are not in the public SDK |
| `android/content/res/XResources.java` | `XTypedArray` calls `super()` instead of the hidden `super(null, null, null, 0)` | `TypedArray`'s hidden constructor is not in the public SDK |
| `android/content/res/XModuleResources.java` | `AssetManager#addAssetPath` is invoked reflectively | `addAssetPath` is a hidden framework API |

Everything else is upstream code, unmodified. `../xposed-api82-stubs` holds compile-only stubs
for the hidden framework classes the sources reference; they are consumed with `compileOnly` and
are never packaged.
