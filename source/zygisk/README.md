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
- The legacy API is `de.robv.android.xposed:api:82` (Apache-2.0). The published artifact is a
  **compile-time stub** - every method body throws `RuntimeException("Stub!")` - which LSPosed
  hides behind the framework's own classes. The Zygisk payload has no framework, so it packages
  the upstream API 82 implementation from `source/xposed-api82` instead (see
  `source/xposed-api82/PROVENANCE.md`). Its Java dispatcher supplies callback priority/order,
  before/after semantics, `hookMethod`, `hookAllMethods`, `hookAllConstructors`, callback removal
  on `Unhook`, `XC_LoadPackage` and `IXposedHook*` types, and `XposedHelpers`.
- `invokeOriginalMethodNative` calls the LSPlant backup method retained by `HookRuntime`. For ordinary instance methods, the receiver is passed as the reflective invocation receiver; static methods use a null receiver. Constructor behavior still requires device-level validation.

## Build

```sh
./source/gradlew --no-daemon -p source -PzygiskPort=true :app:testDebugUnitTest :app:assembleDebug
PAYLOAD_APK=source/app/build/outputs/apk/debug/app-debug.apk \
  PAYLOAD_KIND=source-rebuild source/zygisk/build-zygisk.sh
```

The packaging script requires an explicit source-rebuilt APK and verifies that all six Zygisk Java bridge classes and all three native hook libraries are present. The uploaded original APK is retained separately in ProductOS project storage and its SHA-256 has been verified, but it predates these bridge classes; even with an exact hash it cannot function as the runnable Zygisk payload. PAYLOAD_KIND=original is therefore deliberately rejected rather than producing a non-working installer. The CI workflow packages a source-rebuilt debug APK, which is not byte-identical to the uploaded original.

## Source-level checks

```sh
JAVA_HOME=/path/to/jdk ANDROID_JAR=/path/to/android.jar \
  source/zygisk/tests/verify_local_port.sh
```

This compiles the port package and the vendored API 82 implementation, runs the unit tests and
re-checks the JNI registration table, the vendored upstream sources, the pinned OpenSSL archives
and the packaging-script refusals. It needs a JDK and a public `android.jar`; the full Gradle
build still belongs to CI.

## Current limits

- Full `XResources`/`XModuleResources` replacement and package-resource callbacks are not
  implemented; the vendored API keeps the upstream classes so the payload still links, but the
  resource substitution paths are unreachable in this port.
- `setObjectClass` and `cloneToSubclass` are explicitly unsupported. File-before-fork hooks are no-ops in an already-specialized app process.
- Constructor original-method invocation, Tinker loader replacement, ART symbol resolution, and both ARM ABIs need real QQ/TIM device tests across Android versions.
- The exact original APK is present in ProductOS project storage at `public/payloads/xautodaily/XAutoDaily_3.1.0-rc6.apk` and its downloaded SHA-256 matches the user-supplied hash. It is byte-identical to the supplied attachment. It is not used as the runtime payload because it lacks the Zygisk bridge classes added by this port.

See `source/docs/ZYGISK_PORT_3.1.0-RC6.md` for the implementation and validation ledger.
