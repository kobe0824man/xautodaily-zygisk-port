# XAutoDaily Zygisk port

This is an in-progress, source-based port of XAutoDaily 3.1.0-rc6. Its baseline is the uploaded APK's embedded source revision `09618e67d7441c24b0aad149be3485fcb2eab91d`; the upstream repository is used only for reference and is not modified.

## Runtime path

1. The Zygisk API v5 loader filters QQ/TIM processes, stages `payload.apk` in the app-private `code_cache`, and loads only `Bootstrap` with the system class loader. It deliberately does **not** query `ActivityThread.currentActivityThread()` in `postAppSpecialize`, because ActivityThread has not been created yet.
2. `Bootstrap` extracts the ABI-matched native libraries, creates the module `DexClassLoader`, loads LSPlant/Dobby, and installs a one-shot API-82 hook on `ActivityThread.handleBindApplication`.
3. `BindApplicationHook` runs before the target `Application` is created. It reads `AppBindData.info.getClassLoader()`, installs that `LoadedApk` loader in `HostClassLoaderBridge`, and only then loads `MainHook` and invokes `IXposedHookZygoteInit.initZygote` followed by `IXposedHookLoadPackage.handleLoadPackage`.
4. `MainHook` also updates the bridge when Tinker replaces the host class loader.

## Hook backend

- LSPlant 6.4 provides ART method/constructor hooks and backup methods.
- Dobby provides LSPlant's inline hook/unhook callbacks.
- The JNI bridge resolves ART symbols through `dlsym`, Dobby's resolver, and loaded/on-disk ELF symbol tables.
- The packaged legacy API is `de.robv.android.xposed:api:82`. Its Java dispatcher supplies callback priority/order, before/after semantics, `hookMethod`, `hookAllMethods`, `hookAllConstructors`, callback removal on `Unhook`, `XC_LoadPackage` and `IXposedHook*` types, and `XposedHelpers`.
- `invokeOriginalMethodNative` calls the LSPlant backup method retained by `HookRuntime`. For ordinary instance methods, the receiver is passed as the reflective invocation receiver; static methods use a null receiver. Constructor behavior still requires device-level validation.

## Build

```sh
./source/gradlew --no-daemon -p source -PzygiskPort=true :app:testDebugUnitTest :app:assembleDebug
PAYLOAD_APK=source/app/build/outputs/apk/debug/app-debug.apk \
  PAYLOAD_KIND=source-rebuild source/zygisk/build-zygisk.sh
```

The packaging script requires an explicit APK and records its kind and SHA-256 in `payload.info`. It never fabricates a payload. The CI workflow builds the source-rebuilt debug APK; that APK is not byte-identical to the uploaded original.

## Current limits

- Full `XResources`/`XModuleResources` replacement and package-resource callbacks are not implemented.
- `setObjectClass` and `cloneToSubclass` are explicitly unsupported. File-before-fork hooks are no-ops in an already-specialized app process.
- Constructor original-method invocation, Tinker loader replacement, ART symbol resolution, and both ARM ABIs need real QQ/TIM device tests across Android versions.
- The original chat attachment is not present in the remote ProductOS project. No ZIP built from this repository may be described as containing the original APK until the exact bytes are supplied as `PAYLOAD_APK`.

See `source/docs/ZYGISK_PORT_3.1.0-RC6.md` for the implementation and validation ledger.
