# Native hook dependencies

- **LSPlant**: `org.lsposed.lsplant:lsplant-standalone:6.4` (Maven Central, LGPL-3.0). The corresponding upstream tag is `v6.4`, commit `7217ac6f41e2bda549e4acb54e632abb03e5ccaf`. Its Prefab header and prebuilt Android shared library are linked by the app's native build.
- **Dobby**: Git submodule `source/zygisk/third_party/Dobby`, pinned at `5dfc8546954ce3b3198132ab13fddb89ee92cdd7` (Apache-2.0). It supplies the inline hook/unhook functions passed to LSPlant.
- **Xposed API**: API 82 (Apache-2.0). The published `de.robv.android.xposed:api:82` artifact is a
  compile-time stub whose method bodies all throw `RuntimeException("Stub!")`; the Zygisk payload
  therefore packages the upstream API 82 implementation vendored in `source/xposed-api82`
  (see `source/xposed-api82/PROVENANCE.md`), and the native bridge registers the API-82
  `XposedBridge` native entry points on it and routes calls through LSPlant. The vendored tree is
  checked against the pinned upstream source JAR by `tests/verify_xposed_api82.py`.

The Magisk `update-binary` and GPL-3.0 license are copied from `topjohnwu/Magisk` commit `a212cd92b74174f87c720ff849461a90943b382f` (`scripts/module_installer.sh` and `LICENSE`). See `module/META-INF/com/google/android/`.

- **OpenSSL 1.1.1t**: headers and libcrypto.a / libssl.a for arm64-v8a and armeabi-v7a are pinned to LuckyPray/openssl-android commit 11794e5c6fd304b6d434acddd195b40e78e7603a. The static archive Git blob IDs and ABI checks are recorded in ../openssl/BUILD-PROVENANCE.md and enforced by tests/verify_openssl_archives.py.
