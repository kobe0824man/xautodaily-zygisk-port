---
title: Run CI from the owner's personal GitHub account
type: decision
scope: project
---

The GitHub Actions workflows are hosted in `kobe0824man/xautodaily-zygisk-port`, the repository
owner's personal account, instead of the organisation repository
`productos-agents/productos-xautodaily-zygisk-port-4f7949f5`.

**Why the organisation cannot run jobs** — established by API inspection, not inference:

- All 34 workflow runs in the organisation repository are `completed / failure`. There is no
  successful run, ever, including runs of `.github/workflows/actions-selfcheck.yml`, whose only
  step is `echo`.
- The job objects report `runner_name` empty and contain **zero steps**. The job is created and
  then discarded before a runner is scheduled, so no build instruction ever executed.
- `GET /orgs/productos-agents/actions/permissions` reports `enabled_repositories: all`,
  `allowed_actions: all`. `GET /repos/.../actions/permissions` reports `enabled: true`,
  `allowed_actions: all`. Actions is not disabled and no action is filtered out.
- A job that is created and dropped without a runner, with all policies enabled, is the
  signature of an exhausted private-repository minutes/billing pool. Private-repository minutes
  are drawn from the organisation's shared pool, which is where this project was hitting its
  ceiling.

**How** — the port branch, the workflow files and the build contract are unchanged; only the
hosting repository moves. The personal account draws on its own Actions allocation, so the
organisation's exhausted pool no longer applies.

**What changed in the workflows at the same time** (the build had never executed, so it was
hardened before its first real run):

- CMake/Ninja for the native build come from the pinned SDK package 3.22.1
  (`CMAKE_BIN`/`NINJA_BIN`) rather than the runner image's toolchain, so the build uses the
  toolchain the workflow declares.
- The job timeout is 120 minutes: a cold runner has to download the SDK platform, build tools,
  the NDK and CMake, resolve every Gradle dependency, build the app and then build the native
  loader for two ABIs.
- `concurrency` cancels superseded runs on the same ref, because each run costs real minutes.

**Consequence**: the organisation repository keeps the history and the source of record, but the
authoritative Gradle/JNI build now runs in the personal repository. The level-2 evidence in
[[concept-port-validation]] depends on that repository staying healthy.
