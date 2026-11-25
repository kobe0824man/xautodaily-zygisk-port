# Project Memory

One durable fact per bullet — decisions, preferences, constraints, gotchas.
Newest at the bottom. Curate in place: update what changed, delete what is
wrong, merge duplicates, flag contradictions with ⚠️. Use ⛔ for hard
"never do this" rules. When this file passes ~200 lines, consolidate detail
into topic files in this folder and keep a one-line pointer here.
Detail pages: `<topic>.md` next to this file. Journal: `log.md` (append-only).
Never store secrets, API keys, or tokens here.

## Facts
- Port work belongs on the branch `zygisk-port-3.1.0-rc6` of `productos-agents/productos-xautodaily-zygisk-port-4f7949f5`; `main` carries the upstream sources plus the platform's own edits. The upstream LuckyPray/XAutoDaily project is never modified.
- The published `de.robv.android.xposed:api:82` artifact is a compile-time stub — every method body throws `RuntimeException("Stub!")` and only `getXposedVersion` is native. ⛔ Never depend on it at runtime.
- The Zygisk payload has no framework to shadow that stub, so it vendors the upstream API 82 implementation as the Gradle module `:xposed-api82` (sources pinned to `api-82-sources.jar`, Apache-2.0); only `-PzygiskPort=true` builds use it.
- `:xposed-api82-stubs` holds compile-only stubs for the hidden framework APIs the upstream sources reference and is consumed with `compileOnly`, so it never reaches the APK.
- API 82's `XposedBridge` declares exactly ten native methods. The JNI table in `source/app/src/main/cpp/zygisk_hook.cpp` must match them exactly — `RegisterNatives` rejects the whole table otherwise, which kills `JNI_OnLoad`.
- `source/zygisk/tests/verify_local_port.sh` is the only executable verification while CI is blocked: it compiles the port package and the vendored API against a public `android.jar`, runs the unit tests, checks the JNI table, the vendored upstream provenance, the OpenSSL archives and the packaging refusals.
- The sandbox has no Gradle, Android SDK/NDK, CMake or Ninja; a Temurin JDK 21 and a public `android.jar` can be fetched directly for source-level checks.
- The platform's background sync rewrites the working tree and silently reverts uncommitted edits. Commit small changes immediately instead of batching them.
- GitHub Actions cannot start any job for the `productos-agents` organisation: every run fails in under two seconds with no runner, no steps and an empty log, including a trivial self-check workflow. The repository owner has to resolve the organisation's Actions billing/quota.
- The port has never been device-tested: no rooted device, no QQ/TIM startup, no hook coverage, no ABI matrix.
