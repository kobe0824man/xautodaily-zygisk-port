# XAutoDaily Zygisk port

In-progress port based on source revision `09618e67d7441c24b0aad149be3485fcb2eab91d` and the uploaded 3.1.0-rc6 APK metadata. The original upstream repository is not modified.

- Targets: QQ/TIM, `arm64-v8a` and `armeabi-v7a`.
- Loader: Zygisk API v5, payload staged into the host app's private code cache.
- Java runtime: child-first module loader, API-82 `XposedBridge` callback dispatcher, direct `IXposedHookZygoteInit`/`IXposedHookLoadPackage` entry dispatch.
- Hook backend: LSPlant 6.4 + Dobby, with ART symbol resolution from `libart.so`.
- Build: `./source/gradlew -p source -PzygiskPort=true :app:testDebugUnitTest :app:assembleDebug`; then set `PAYLOAD_APK` to the resulting debug APK and run `source/zygisk/build-zygisk.sh`.

The package script refuses to invent a payload. The CI archive uses a source-rebuilt debug APK and is not the original uploaded APK. A source-rebuild ZIP remains a candidate until Android/QQ/TIM runtime tests pass. See `source/docs/ZYGISK_PORT_3.1.0-RC6.md` and `source/zygisk/DEPENDENCIES.md` for scope and limitations.
