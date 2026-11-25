# XAutoDaily Zygisk port — 3.1.0-rc6

## Baseline and provenance
- User-supplied APK: `XAutoDaily_3.1.0-rc6.apk`
- SHA-256: `2d337bb750d0187d6e6eb06fa0429bd3e37b0f2841b904f6d01d08c6886413df`
- APK manifest: package `me.teble.xposed.autodaily`, versionName `3.1.0-rc6`, versionCode `26112014`
- Embedded source revision: `09618e67d7441c24b0aad149be3485fcb2eab91d`
- The pinned source Gradle file still says `appVerName = 3.0.37`; the Zygisk build-only Gradle property `-PzygiskPort=true` overrides the APK metadata to match the inspected baseline manifest without changing normal upstream build defaults.
- Supported native ABIs: `arm64-v8a`, `armeabi-v7a`. The uploaded APK has `libxa_native.so` for these two ABIs only.

## Implemented in source
1. **Java bootstrap** — `Bootstrap.start(ClassLoader,String,String,String)` is now implemented. It stages ABI-matched native libraries from the payload APK, creates a child-first module `DexClassLoader`, preserves host-first resolution for Android and QQ/TIM classes, and invokes the module entry in the correct order: `initZygote` then `handleLoadPackage`. `XC_LoadPackage.LoadPackageParam` is populated with package/process names, the host LoadedApk class loader, `ApplicationInfo` when readable, and `isFirstApplication`.
2. **API 82 runtime** — `de.robv.android.xposed:api:82` is now packaged (not compile-only). The API's callback dispatcher supplies callback ordering, exception handling, `hookMethod`, `hookAllConstructors`, `unhook` callback removal, and `invokeOriginalMethod` entry points. The Zygisk JNI library registers the API-82 native methods.
3. **LSPlant + Dobby** — `org.lsposed.lsplant:lsplant-standalone:6.4` is linked through Prefab; Dobby is pinned as a submodule at `5dfc8546954ce3b3198132ab13fddb89ee92cdd7`. The JNI bridge initializes LSPlant with Dobby inline hook/unhook callbacks and an ART symbol resolver that tries `dlsym`, Dobby's resolver, then ELF `.dynsym`/`.symtab` lookup from the loaded `libart.so`.
4. **Original-method bridge** — LSPlant backup methods are stored in `HookRuntime`; the Xposed API-82 `invokeOriginalMethodNative` calls those backups with the receiver prepended for instance methods and constructors. LSPlant accepts `Executable`, so both `Method` and `Constructor` targets are passed through.
5. **Resources** — `MainHook` no longer calls the API-stub `XModuleResources.createInstance` or `EzXHelperInit.initZygote`; the existing project-specific `modulePath`/AssetManager resource injection remains the intended path. General Xposed `XResources` replacement APIs are not implemented.
6. **Build metadata** — a reproducible `-PzygiskPort=true` build uses the inspected 3.1.0-rc6 manifest version and code. A native-only Zygisk build script and a Magisk module packaging script are present. The package script requires an explicit `PAYLOAD_APK` and records its kind and SHA-256 in `payload.info`.

## Validation status
- Source-level implementation is present; a JUnit test covers instance backup receiver marshalling, static-method reflection fallback, and constructor fallback.
- Android/NDK compilation is delegated to `.github/workflows/xautodaily-zygisk-ci.yml`; no local Android SDK/JDK is available in the ProductOS project shell. CI results must be checked before claiming a successful build.
- No QQ/TIM device or emulator integration test has yet been completed. No claim is made that hooks work across Android releases or both ABIs at runtime.
- The chat attachment's APK bytes are still not present in the remote ProductOS project. The CI candidate uses a **new debug APK rebuilt from the pinned source**, not the original uploaded APK. It is not binary-identical and is not a release-signed replacement. The original APK is not embedded unless a later build explicitly receives that exact file as `PAYLOAD_APK` with `PAYLOAD_KIND=original`.

## Known limitations / next validation
- API-82 `unhook()` removes the callback from its `CopyOnWriteSortedSet`; the LSPlant trampoline remains installed and transparently calls the backup when the callback set becomes empty. This follows the API-82 Java semantics, but does not reclaim the ART hook.
- `XposedBridge.setObjectClass`, `cloneToSubclass`, full XResources/XModuleResources replacement, and package-resource callbacks are not implemented. They are not used by the inspected XAutoDaily hook entry path, but broader API-82 compatibility is incomplete.
- LSPlant 6.4 is pinned to the published upstream artifact; Android 16/17 compatibility and ART symbol availability require real-device verification.
- Tinker class-loader replacement is handled by the existing `MainHook` callback and has not been validated under Zygisk.
- The generated source-rebuild ZIP is a **candidate only** until CI succeeds and QQ/TIM runtime tests pass.
