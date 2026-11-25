# Log

Append-only. One line per meaningful change.

## [2026-11-25T03:40:00Z] update | Fixed the API 82 runtime: vendored the upstream implementation, added compile-only hidden-API stubs, and proved the JNI table matches the pinned upstream sources.
## [2026-11-25T03:12:00Z] update | Compiled the port package with a JDK 21 and the public android.jar: fixed `android.app.ApplicationInfo` -> `android.content.pm.ApplicationInfo` and the unreported `InstantiationException`; made the HookRuntime tests order-independent.
## [2026-11-25T03:08:00Z] update | Proved GitHub Actions cannot start jobs for the organisation: a trivial self-check workflow fails identically (no runner, no steps, empty log).
## [2026-11-25T03:02:00Z] update | Established that the published `de.robv.android.xposed:api:82` artifact is a stub whose method bodies all throw `RuntimeException("Stub!")`.
