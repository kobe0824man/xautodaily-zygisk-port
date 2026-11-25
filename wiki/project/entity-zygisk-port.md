---
title: XAutoDaily Zygisk port
type: entity
scope: project
---

Port of the XAutoDaily Xposed module to a standalone Zygisk module.

- Upstream baseline: uploaded APK `XAutoDaily_3.1.0-rc6.apk`, embedded source revision
  `09618e67d7441c24b0aad149be3485fcb2eab91d`, package `me.teble.xposed.autodaily`,
  versionName `3.1.0-rc6`, versionCode `26112014`.
- Native ABIs: `arm64-v8a`, `armeabi-v7a`.
- Repository: `productos-agents/productos-xautodaily-zygisk-port-4f7949f5`. Port work lives on the
  branch `zygisk-port-3.1.0-rc6`; `main` mirrors the upstream sources plus the platform's own
  edits. The upstream LuckyPray/XAutoDaily project is not modified.
- Module layout: `source/app` (payload APK), `source/zygisk` (Zygisk loader, Magisk module,
  packaging scripts, verifiers), `source/xposed-api82` (vendored legacy API runtime),
  `source/xposed-api82-stubs` (compile-only hidden-framework stubs), `source/docs` (port ledger).
- Port build flag: `./source/gradlew -p source -PzygiskPort=true :app:testDebugUnitTest :app:assembleDebug`.

Runtime path: Zygisk `postAppSpecialize` -> `Bootstrap.start` -> one-shot hook on
`ActivityThread.handleBindApplication` -> `BindApplicationHook` installs the real `LoadedApk`
loader -> `MainHook.initZygote` + `handleLoadPackage`.
