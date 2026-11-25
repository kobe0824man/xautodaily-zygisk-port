# XAutoDaily Zygisk port — 3.1.0-rc6

## Baseline and provenance

- User-supplied APK: `XAutoDaily_3.1.0-rc6.apk`
- SHA-256: `2d337bb750d0187d6e6eb06fa0429bd3e37b0f2841b904f6d01d08c6886413df`
- APK manifest: package `me.teble.xposed.autodaily`, versionName `3.1.0-rc6`, versionCode `26112014`
- Embedded source revision: `09618e67d7441c24b0aad149be3485fcb2eab91d`
- The pinned source Gradle file says `appVerName = 3.0.37`; the Zygisk-only `-PzygiskPort=true` build overrides versionName/versionCode to the inspected APK manifest without changing normal upstream defaults.
- Native ABIs: `arm64-v8a`, `armeabi-v7a`. The uploaded APK's `libxa_native.so` exists for these ABIs only.
- The original LuckyPray/XAutoDaily repository is not modified. Port changes live on `zygisk-port-3.1.0-rc6` of `productos-agents/productos-xautodaily-zygisk-port-4f7949f`; the CI build runs from the mirror in the owner's personal account, `kobe0824man/xautodaily-zygisk-port`, because the organisation cannot schedule a runner.

## Implemented in source

1. **Deferred Java bootstrap** — `Bootstrap.start(String,String,String)` is called from Zygisk `postAppSpecialize` with a system-classloader parent. It does not try to read `ActivityThread.currentActivityThread()` before ActivityThread exists. It stages ABI-matched libraries, creates a module `DexClassLoader` with a mutable `HostClassLoaderBridge`, loads the native backend, and installs a one-shot hook on `ActivityThread.handleBindApplication`.
2. **Actual host LoadedApk loader** — `BindApplicationHook` runs before `Application` creation, reads `AppBindData.info.getClassLoader()`, verifies the package, and installs that loader in `HostClassLoaderBridge`. Only then are `MainHook` and its module classes loaded. `initZygote` is invoked before `handleLoadPackage`; `XC_LoadPackage.LoadPackageParam` receives package name, process name, host class loader, `ApplicationInfo`, and `isFirstApplication`. `MainHook` updates the bridge when Tinker changes the host loader.
3. **Legacy Xposed API 82** — the published `de.robv.android.xposed:api:82` artifact is a *compile-time stub*: every method body is `throw new RuntimeException("Stub!")` and only `getXposedVersion` is left native. LSPosed hides that stub behind the framework's own `de.robv.android.xposed.*` classes, but the Zygisk payload has no framework, so the packaged stub would have been the implementation and every `XposedBridge.hookMethod`, `XposedBridge.invokeOriginalMethod` and `XposedHelpers.findAndHookMethod` call would have thrown at runtime. The port therefore packages the upstream API 82 implementation, vendored in `source/xposed-api82` (Apache-2.0, see `source/xposed-api82/PROVENANCE.md`), with compile-only stubs for the hidden framework APIs the upstream sources reference. API 82 supplies `XposedHelpers`, `XC_MethodHook`, callback ordering and exception handling, `hookMethod`, `hookAllMethods`, `hookAllConstructors`, `Unhook`, `IXposedHookLoadPackage`, `IXposedHookZygoteInit`, and `XC_LoadPackage` structures. The bootstrap sets the ART runtime value, `isZygote=false`, and the compatibility version field. Only `-PzygiskPort=true` builds use the vendored implementation; normal upstream builds keep the published artifact.
4. **Native API-82 registration** — the JNI bridge registers exactly the 10 native methods declared by the pinned API-82 XposedBridge, including initNative, with matching JNI descriptors. source/zygisk/tests/verify_xposed_api82.py compares method names and descriptors against the API-82 source JAR and checks that the vendored tree matches that JAR byte for byte apart from two documented adaptations. The previous table contained undeclared native methods and would have caused RegisterNatives / JNI_OnLoad to fail. Hook installation uses LSPlant; invokeOriginalMethodNative calls the registered LSPlant backup through HookRuntime. API-82 Unhook removes callbacks; the LSPlant trampoline remains and falls through to the backup when the callback set is empty.
5. **LSPlant + Dobby** — `org.lsposed.lsplant:lsplant-standalone:6.4` is linked through Prefab. Dobby is pinned as submodule `5dfc8546954ce3b3198132ab13fddb89ee92cdd7`. The JNI bridge supplies Dobby inline hook/unhook functions and ART symbol resolvers using `dlsym`, Dobby's resolver, and loaded/on-disk ELF `.dynsym`/`.symtab` lookup. LSPlant's `Hook` takes the callback as a `java.lang.reflect.Method` *object* — it checks that the argument is a `java.lang.reflect.Executable` and reads the callback's name and declaring class from it — so the bridge reflects `XposedHooker.dispatch` with `ToReflectedMethod` before calling it. Passing the `jmethodID` instead would not compile (`jmethodID` is `struct _jmethodID *`, unrelated to `jobject`, which is `class _jobject *` in the NDK's `jni.h`) and would not satisfy LSPlant's own type check at runtime.
6. **Native API verification** — `source/zygisk/tests/verify_native_api.py` pins the native bridge against the pinned LSPlant and Dobby headers: every `lsplant::` symbol it calls, every `InitInfo` field it assigns, and every argument type it passes must exist in, and be accepted by, the pinned headers. This is the only check that covers the C++ bridge, which the source-level suite never compiles.
7. **Packaging guardrails** — the native loader and Magisk module scripts require an explicit `PAYLOAD_APK`; `payload.info` records its kind and SHA-256. The packager verifies that all six Zygisk Java bridge classes and all three native hook libraries are available. `PAYLOAD_KIND=original` first checks the exact uploaded APK SHA-256, then rejects it as a runtime payload because those bridge classes are absent. ProGuard keeps the bootstrap and JNI entry classes by stable name.

## Validation ledger

- **Repository**: working tree and target branch inspected; port changes are being committed only to `zygisk-port-3.1.0-rc6`.
- **Source tests added**: `HostClassLoaderBridgeTest` checks that the host loader can be installed after bootstrap starts and that the host loader cannot shadow the bridge or bootstrap classes; `HookRuntimeTest` covers instance/static backup invocation and reflection fallbacks. The backup registry is cleared per test, because `Method`/`Constructor` equality made the old tests order-dependent: `fallsBackToReflectionForUnhookedStaticMethods` returned 27 instead of 18 when it ran after the backup test. Shell syntax, `git diff --check`, the missing-payload refusal, the bogus-kind refusal and the original-payload SHA-256 refusal were checked locally. These are source-level checks until Gradle CI runs.
- **Local compile and unit-test run**: with Temurin JDK 21, Android API 36 `android.jar`, JUnit 4.13.2 and the API-82 artifact, the Zygisk Java package compiles and all 7 `HostClassLoaderBridgeTest` / `HookRuntimeTest` tests pass. The vendored API 82 implementation also compiles against the public SDK (66 classes). These checks are narrower than the full Gradle app build and do not validate JNI/NDK linkage.
- **Vendored API 82 compile**: the upstream API 82 implementation compiles against the public SDK with the documented adaptations (66 classes), and `javap` confirms the packaged `XposedBridge` declares exactly the 10 native methods the JNI table registers.
- **Native registration audit**: API-82 source JAR audit passes for all 10 native declarations and descriptors. initNative is registered as a no-op success; the bridge table contains no undeclared API methods.
- **Repeatable local checks**: `source/zygisk/tests/verify_local_port.sh` runs the whole source-level
  suite (package compile, unit tests, vendored API 82 compile, JNI table, vendored provenance,
  OpenSSL archives, native API against the pinned LSPlant/Dobby headers, packaging refusals) with a
  JDK and a public `android.jar`. It passed on the current tree.
- **Native bridge defect found and fixed**: LSPlant 6.4's `Hook` takes the callback as a
  `java.lang.reflect.Method` object, but the bridge passed the `jmethodID` of
  `XposedHooker.dispatch`. In the NDK's `jni.h`, `jmethodID` is `struct _jmethodID *` and `jobject`
  is `class _jobject *` — unrelated types — so the call could not compile, and even with a
  permissive compiler it would have failed LSPlant's own `java.lang.reflect.Executable` check at
  runtime. The bridge now reflects the method ID with `ToReflectedMethod` before calling `Hook`.
  Nothing compiled the C++ bridge before this, because no job ever got a runner.
  `source/zygisk/tests/verify_native_api.py` now pins that contract against the upstream LSPlant
  6.4 header and the pinned Dobby header, and reports the argument type mismatch when
  reintroduced.
- **OpenSSL dependency repair**: the exact four OpenSSL 1.1.1t static archives were restored from the pinned upstream commit; Git blob hashes and ELF ABI checks are enforced by verify_openssl_archives.py.
- **Local build limitation**: the shell has no Gradle, Android SDK/NDK, CMake or Ninja toolchain, so a local Gradle/JNI build of the app module still cannot be claimed. A JDK 21 and the public `android.jar` are available, which is what made the package-level compile and the unit tests above possible. The GitHub Actions workflow must provide the authoritative full-build result.
- **CI failure point — confirmed and measured**: the organisation's Actions environment, not the
  workflow or the sources. Every one of the 34 workflow runs in the repository is
  `completed / failure`, including runs of the trivial self-check workflow that only calls
  `echo`; there is not a single successful run. The job objects report `runner_name` empty and
  contain **no steps at all**, so the job is rejected before a runner is scheduled rather than
  failing during execution. The organisation policy (`/orgs/productos-agents/actions/permissions`)
  and the repository policy both report `enabled: true`, `allowed_actions: all`, and
  `sha_pinning_required: false`, so Actions is not disabled and no action is filtered. The
  organisation's Actions usage for November 2026 measures **1,906 Linux minutes** plus 24 Windows
  and 12 macOS minutes against the free plan's included allowance, and private-repository minutes
  are charged at double rate against that allowance. This repository itself consumed **zero**
  minutes of that budget despite 34 attempts, which is exactly what a job rejected before
  scheduling looks like. The check-run annotations endpoint is not readable with the platform
  token, so the exact billing wording cannot be quoted, but the failure is definitely not a
  source compile failure: it is an exhausted private-repository Actions allocation.
- **CI relocation**: the build workflows are moved to the repository owner's personal GitHub
  account (`kobe0824man/xautodaily-zygisk-port`), whose Actions allocation is personal rather
  than organisational, so the organisation's exhausted pool no longer applies. The branch name,
  the workflow files and the build contract are unchanged; only the hosting repository moves.
  `tools/relocate_ci_to_personal_repo.sh` performs the relocation and verifies the result. The
  workflows were hardened for their first real execution at the same time: the CMake/Ninja
  used by the native build are now taken from the pinned SDK package (3.22.1) instead of the
  runner image's toolchain, the job timeout is 120 minutes for the cold SDK/NDK download and the
  two-ABI native build, and superseded runs on the same ref are cancelled.

- **Device test**: no rooted Android device or emulator with QQ/TIM has been used. The new `handleBindApplication` timing, ART symbol resolver, constructor original-call path, Tinker loader update, native dependencies, and both ABIs remain unverified at runtime.
- **Original APK bytes**: the exact APK is stored remotely at `public/payloads/xautodaily/XAutoDaily_3.1.0-rc6.apk`; a fresh download measured 9,581,427 bytes and SHA-256 `2d337bb750d0187d6e6eb06fa0429bd3e37b0f2841b904f6d01d08c6886413df`. It is byte-identical to the supplied attachment. However, it predates the port's Bootstrap/BindApplicationHook/HostClassLoaderBridge/HookRuntime/XposedHooker/NativeLoader classes. The packager now rejects it as a runtime payload even with a matching hash; only the source-rebuilt APK can run this Java bridge. No installable ZIP has been produced.

## Explicitly incomplete

- Full `XResources`/`XModuleResources` replacement and package-resource callbacks. The vendored upstream classes are present so the payload links, but the resource substitution paths are not reachable in this port.
- `XposedBridge.setObjectClass` and `cloneToSubclass` native behavior (explicitly fail as unsupported).
- Device-verified constructor `invokeOriginalMethod` behavior and complete compatibility with every API-82 edge case.
- Android-version/ABI matrix tests, QQ/TIM startup stability, hook coverage, and Tinker runtime validation.
- A working runtime based on the exact original APK bytes: that APK does not contain the new Java bridge classes, and the current port does not inject a separate DEX overlay. The exact original is archived in ProductOS storage but is not a runnable payload.

The implementation is a substantial port in progress, not a completed or device-validated replacement for the LSPosed module.
