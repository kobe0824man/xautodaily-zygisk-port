# Overview

XAutoDaily Zygisk Port is an in-progress port of the XAutoDaily Xposed module (QQ/TIM automation)
to a standalone Zygisk module, based on the uploaded `XAutoDaily_3.1.0-rc6.apk`
(SHA-256 `2d337bb7…6413df`). The port runs inside the target app process with no LSPosed
framework: the payload APK carries its own bootstrap, a deferred `ActivityThread.handleBindApplication`
hook that installs the real `LoadedApk` class loader, an LSPlant/Dobby native hook backend, and a
JNI bridge that registers the legacy Xposed API 82 native entry points.

The decisive constraint of this project is that the payload must be self-sufficient. That is why
the legacy API 82 runtime is vendored (`source/xposed-api82`) rather than consumed from the
published artifact, which is a compile-time stub, and why the native registration table is checked
against the upstream API 82 sources instead of trusted.

The port is not device-validated: no rooted device has run it, and the organisation's GitHub
Actions cannot currently start any job, so the full Gradle/JNI build has never been executed.
