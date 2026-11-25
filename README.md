# XAutoDaily Zygisk Port

Pinned XAutoDaily source baseline plus an in-progress standalone Zygisk migration for QQ/TIM,
targeting ARM64 and ARM32.

See `source/docs/ZYGISK_PORT_3.1.0-RC6.md` for the exact baseline, completed work and blockers,
and `wiki/` for the project's decision record.

## Branches

| Branch | Contents |
| --- | --- |
| `zygisk-port-3.1.0-rc6` | The port. All Zygisk work lives here. |
| `main` | Upstream sources plus platform edits; the base for pull requests. |

## CI

Two GitHub Actions workflows run from this repository:

- **XAutoDaily Zygisk CI** (`.github/workflows/xautodaily-zygisk-ci.yml`) — the authoritative
  build. It installs the pinned Android SDK 36 / NDK 27.0.12077973 / CMake 3.22.1, runs the unit
  tests, rebuilds the payload APK from source, checks the Xposed API 82 JNI registration table
  against the pinned upstream sources, builds the Zygisk loader for `arm64-v8a` and
  `armeabi-v7a`, packages the candidate module ZIP and verifies the APK metadata and native ELF
  exports. The candidate APK, the ZIP and the test reports are uploaded as artifacts.
- **Actions self-check** (`.github/workflows/actions-selfcheck.yml`) — a ten-second job that only
  prints its environment. It exists to prove that a runner is actually allocated, so a failure of
  the real build can be attributed to the build rather than to the CI environment.

Both workflows trigger on pushes to `zygisk-port-3.1.0-rc6`; the main CI also runs on pull
requests targeting that branch and on manual dispatch.

### Why CI runs in the personal repository

This project was originally built in the `productos-agents` organisation. GitHub never allocated
a runner there: across 34 workflow runs every job — including a self-check job that only calls
`echo` — was created and then failed in under two seconds with no runner, no steps and no log.
The organisation and repository Actions policies were verified to be fully enabled with all
actions allowed, so the rejection happened before scheduling, in the organisation's private
repository minutes pool.

The workflows therefore run in the repository owner's personal account, which draws on that
account's own Actions allocation instead of the organisation's.

## Local verification

The sandbox has no Gradle, Android SDK/NDK, CMake or Ninja, so the Gradle build cannot run
locally. Everything that a plain JDK and the public `android.jar` can prove is covered by:

```bash
export JAVA_HOME=/path/to/jdk-21
export ANDROID_JAR=/path/to/android.jar
bash source/zygisk/tests/verify_local_port.sh
```

It compiles the port package, runs the unit tests, compiles the vendored API 82 implementation,
checks the JNI table against the pinned upstream API 82 sources, verifies the pinned OpenSSL
archives against their upstream Git blob IDs, and exercises the packaging-script refusals.
