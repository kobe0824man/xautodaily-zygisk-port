# XAutoDaily Zygisk port — 3.1.0-rc6

## Baseline
- APK filename supplied: `XAutoDaily_3.1.0-rc6.apk`
- Embedded source revision: `09618e67d7441c24b0aad149be3485fcb2eab91d`
- The repository and its four git submodules are pinned to the corresponding commit IDs.
- The pinned source's `app/build.gradle.kts` declares `appVerName = "3.0.37"`; this differs from the uploaded APK filename and must be checked against the APK's manifest/version metadata before claiming exact binary parity.
- Required ABIs: `arm64-v8a`, `armeabi-v7a`.

## Implemented scaffold
- Zygisk API v5 module entry and Magisk module metadata.
- Target-process filter for QQ/TIM.
- Payload FD opened before specialization and exempted from Zygisk's FD closing.
- Payload staging into app-private code cache.
- Host LoadedApk class-loader lookup and dispatch to a Java bootstrap entry.
- The native loader was compiled successfully in the current build sandbox for both ABIs; `zygisk_module_entry` is exported, and ELF headers verify AArch64 ELF64 plus ARM ELF32.

## Remaining work — not release-ready
- Implement `me.teble.xposed.autodaily.zygisk.Bootstrap` in the payload app.
- Integrate LSPlant, a robust ART symbol resolver, and an inline hook backend.
- Implement the Xposed API compatibility runtime required by Xposed API 82 and EzXHelper 1.0.3.
- Preserve callback ordering, original-method invocation, constructor hooks, unhook behavior, class-loader refresh after Tinker, and all initialization ordering.
- Port Xposed module-resource injection.
- Embed the exact uploaded APK and verify the APK version metadata.
- Build and test on actual QQ/TIM installations for both ABIs and supported Android versions.

No installable ZIP is produced by this scaffold. The build script refuses to package without an explicit `PAYLOAD_APK`.
