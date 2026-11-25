# Native hook dependencies

- **LSPlant**: `org.lsposed.lsplant:lsplant-standalone:6.4` (Maven Central). The corresponding upstream tag is `v6.4`, commit `7217ac6f41e2bda549e4acb54e632abb03e5ccaf`. Its Prefab header and prebuilt Android shared library are linked by the app's native build.
- **Dobby**: Git submodule `source/zygisk/third_party/Dobby`, pinned at `5dfc8546954ce3b3198132ab13fddb89ee92cdd7` (Apache-2.0). It supplies the inline hook/unhook functions passed to LSPlant.
- **Xposed API**: API 82 is packaged at runtime from `de.robv.android.xposed:api:82`; the native bridge registers the API-82 `XposedBridge` native entry points and routes calls through LSPlant.

The Magisk `update-binary` is copied from `topjohnwu/Magisk` `scripts/module_installer.sh` and remains under Magisk's GPL-3.0 license. See `module/META-INF/com/google/android/update-binary`.
