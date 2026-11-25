---
title: Zygisk port validation model
type: concept
scope: project
---

Three levels of evidence, and what each can and cannot prove.

1. **Source level** (available in the sandbox): compile the port package and the vendored API 82
   implementation with a JDK 21 and the public `android.jar`; run the unit tests; run the
   verifiers; exercise the packaging-script refusals. `source/zygisk/tests/verify_local_port.sh`
   does all of this. It proves the Java layer compiles and behaves, and that the native contract
   matches upstream - it proves nothing about ART, SELinux, or the app.
2. **CI level**: the Gradle build, the JNI build for both ABIs, and the packaging of the candidate
   ZIP. Available in the owner's personal repository, where the runners are actually allocated;
   the organisation repository cannot schedule a job (see [[decision-ci-personal-repository]]).
3. **Device level**: QQ/TIM startup, hook coverage, Tinker loader replacement, constructor
   original-method invocation, hidden-API access. Never performed; no rooted device is available.

The sandbox has no Gradle, Android SDK/NDK, CMake or Ninja. A Temurin JDK 21 and a public
`android.jar` can be fetched directly, which is what makes level 1 possible.
