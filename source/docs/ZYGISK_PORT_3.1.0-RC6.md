# XAutoDaily Zygisk port — 3.1.0-rc6

## Baseline and provenance

- User-supplied APK: `XAutoDaily_3.1.0-rc6.apk`
- SHA-256: `2d337bb750d0187d6e6eb06fa0429bd3e37b0f2841b904f6d01d08c6886413df`
- APK manifest: package `me.teble.xposed.autodaily`, versionName `3.1.0-rc6`, versionCode `26112014`
- Embedded source revision: `09618e67d7441c24b0aad149be3485fcb2eab91d`
- The pinned source Gradle file says `appVerName = 3.0.37`; the Zygisk-only `-PzygiskPort=true` build overrides versionName/versionCode to the inspected APK manifest without changing normal upstream defaults.
- Native ABIs: `arm64-v8a`, `armeabi-v7a`. The uploaded APK's `libxa_native.so` exists for these ABIs only.
- The original LuckyPray/XAutoDaily repository is not modified. Port changes live in `productos-agents/productos-xautodaily-zygisk-port-4f7949f` on `zygisk-port-3.1.0-rc6`.

## Implemented in source

1. **Deferred Java bootstrap** — `Bootstrap.start(String,String,String)` is called from Zygisk `postAppSpecialize` with a system-classloader parent. It does not try to read `ActivityThread.currentActivityThread()` before ActivityThread exists. It stages ABI-matched libraries, creates a module `DexClassLoader` with a mutable `HostClassLoaderBridge`, loads the native backend, and installs a one-shot hook on `ActivityThread.handleBindApplication`.
2. **Actual host LoadedApk loader** — `BindApplicationHook` runs before `Application` creation, reads `AppBindData.info.getClassLoader()`, verifies the package, and installs that loader in `HostClassLoaderBridge`. Only then are `MainHook` and its module classes loaded. `initZygote` is invoked before `handleLoadPackage`; `XC_LoadPackage.LoadPackageParam` receives package name, process name, host class loader, `ApplicationInfo`, and `isFirstApplication`. `MainHook` updates the bridge when Tinker changes the host loader.
3. **Legacy Xposed API 82** — `de.robv.android.xposed:api:82` is packaged at runtime, not compile-only. API 82 supplies `XposedHelpers`, `XC_MethodHook`, callback ordering and exception handling, `hookMethod`, `hookAllMethods`, `hookAllConstructors`, `Unhook`, `IXposedHookLoadPackage`, `IXposedHookZygoteInit`, and `XC_LoadPackage` structures. The bootstrap sets the ART runtime value, `isZygote=false`, and the compatibility version field.
4. **Native API-82 registration** — the JNI bridge registers the declared API-82 `XposedBridge` native methods. It no longer registers a nonexistent `initNative()` method (which would make `RegisterNatives` fail). Hook installation uses LSPlant; `invokeOriginalMethodNative` calls the registered LSPlant backup through `HookRuntime`. API-82 `Unhook` removes callbacks; the LSPlant trampoline remains and falls through to the backup when the callback set is empty.
5. **LSPlant + Dobby** — `org.lsposed.lsplant:lsplant-standalone:6.4` is linked through Prefab. Dobby is pinned as submodule `5dfc8546954ce3b3198132ab13fddb89ee92cdd7`. The JNI bridge supplies Dobby inline hook/unhook functions and ART symbol resolvers using `dlsym`, Dobby's resolver, and loaded/on-disk ELF `.dynsym`/`.symtab` lookup.
6. **Packaging guardrails** — the native loader and Magisk module scripts require an explicit `PAYLOAD_APK`; `payload.info` records its kind and SHA-256. ProGuard keeps the bootstrap and JNI entry classes by stable name.

## Validation ledger

- **Repository**: working tree and target branch inspected; port changes are being committed only to `zygisk-port-3.1.0-rc6`.
- **Source tests added**: `HostClassLoaderBridgeTest` checks that the host loader can be installed after bootstrap starts; `HookRuntimeTest` covers instance/static backup invocation and reflection fallbacks. These are source-level tests until Gradle CI runs.
- **Native registration audit**: the method table is checked against the API-82 `XposedBridge` declarations; the previous invalid `initNative` registration has been removed.
- **Local build limitation**: the ProductOS project shell has no Java/Gradle/Android SDK/NDK toolchain, so a local Gradle/JNI build cannot be claimed. The GitHub Actions workflow must provide the authoritative compile/test result.
- **Device test**: no rooted Android device or emulator with QQ/TIM has been used. The new `handleBindApplication` timing, ART symbol resolver, constructor original-call path, Tinker loader update, native dependencies, and both ABIs remain unverified at runtime.
- **Original APK bytes**: the chat attachment is still not in the remote ProductOS project. The source-rebuild CI candidate is a newly built debug APK, not the uploaded APK and not byte-identical to it. No final ZIP containing the original APK has been produced or claimed.

## Explicitly incomplete

- Full `XResources`/`XModuleResources` replacement and package-resource callbacks.
- `XposedBridge.setObjectClass` and `cloneToSubclass` native behavior (explicitly fail as unsupported).
- Device-verified constructor `invokeOriginalMethod` behavior and complete compatibility with every API-82 edge case.
- Android-version/ABI matrix tests, QQ/TIM startup stability, hook coverage, and Tinker runtime validation.
- Original-APK embedding and an installable ZIP based on the exact uploaded bytes.

The implementation is a substantial port in progress, not a completed or device-validated replacement for the LSPosed module.
