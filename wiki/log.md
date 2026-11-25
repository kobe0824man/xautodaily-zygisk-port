# Log

Append-only. One line per meaningful change.

## [2026-11-25T04:50:00Z] update | Found and fixed a native bridge defect that nothing had compiled: lsplant::Hook was given a jmethodID where it expects a java.lang.reflect.Method object; added verify_native_api.py to pin the bridge against the pinned LSPlant and Dobby headers.
## [2026-11-25T04:35:00Z] update | Confirmed the CI cause from the API (34/34 runs failed, every job object has an empty runner_name and zero steps, org and repo Actions policies fully enabled) and moved the build to the owner's personal account; added tools/relocate_ci_to_personal_repo.sh and hardened the workflow for its first real execution.
## [2026-11-25T03:40:00Z] update | Fixed the API 82 runtime: vendored the upstream implementation, added compile-only hidden-API stubs, and proved the JNI table matches the pinned upstream sources.
## [2026-11-25T03:12:00Z] update | Compiled the port package with a JDK 21 and the public android.jar: fixed `android.app.ApplicationInfo` -> `android.content.pm.ApplicationInfo` and the unreported `InstantiationException`; made the HookRuntime tests order-independent.
## [2026-11-25T03:08:00Z] update | Proved GitHub Actions cannot start jobs for the organisation: a trivial self-check workflow fails identically (no runner, no steps, empty log).
## [2026-11-25T03:02:00Z] update | Established that the published `de.robv.android.xposed:api:82` artifact is a stub whose method bodies all throw `RuntimeException("Stub!")`.
