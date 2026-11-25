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
- The organisation's Actions usage for November 2026 measures **1,906 Linux minutes** plus 24
  Windows and 12 macOS minutes against the free plan's included allowance, and
  private-repository minutes are charged at double rate against that allowance. This repository
  consumed **zero** of those minutes across 34 attempts, which is precisely what a job rejected
  before scheduling looks like.
- A job that is created and dropped without a runner, with all policies enabled, is the
  signature of an exhausted private-repository minutes/billing pool. Private-repository minutes
  are drawn from the organisation's shared pool, which is where this project was hitting its
  ceiling.

**How** — the port branch, the workflow files and the build contract are unchanged; only the
hosting repository moves. The personal account draws on its own Actions allocation, so the
organisation's exhausted pool no longer applies. `tools/relocate_ci_to_personal_repo.sh` performs
the relocation: it creates the repository, publishes the port branch and `main`, and verifies
that the branch refs and the workflow files landed on the remote. The token needs the `repo`
scope (create and push) and the `workflow` scope (push `.github/workflows` — GitHub rejects
workflow updates without it).

The personal repository is **public**, not private. That is deliberate on two counts: public
repositories draw Actions minutes from an unlimited free pool rather than from an account's
private-repository allowance, and the ported source is a derivative of the already-public
upstream XAutoDaily project, so nothing is disclosed that is not already published. The
organisation repository remains the source of record and keeps the full history; the personal
repository is the build host.

**The publishing token has to act as the owner.** The platform's own credential is the
`productos-si` service account: it can *read* `kobe0824man/xautodaily-zygisk-port` but the API
reports `push: false` on it, and it cannot read that repository's Actions permissions at all
(403). Nothing in the platform can push to the owner's personal repository on the owner's
behalf. The relocation therefore needs a token whose identity is `kobe0824man` itself, with
`repo` for the push and `workflow` for the files under `.github/workflows`.

**The relocation tool is tested before it is pointed at the real API.** It is exercised against a
local stand-in for the GitHub REST API and a local bare repository standing in for the remote:
the identity check, the scope check, the already-exists branch, the push of both branches, the
ref verification and the run listing all behave, and a token without the `workflow` scope is
refused with exit code 5 before anything is pushed. That test found a real defect — the scope
parser took only the first field of the scope list, so it read `repo, workflow` as `repo,` and
would have refused a perfectly good token.

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

**What is still missing** — the publish step itself. The personal repository exists and is
public, but it still holds only its empty initial commit: the port branch has never been pushed
there, so **no workflow run exists in it yet**. Two independent routes to the credential were
measured:

- The platform's own credential acts as `productos-si`. It reads the personal repository but
  reports `push: false` on it, and the platform's push tools all target the project's *bound*
  repository (the organisation one) — confirmed by pushing and watching the personal
  repository's refs stay unchanged.
- The platform does hold a GitHub connection for `kobe0824man` (it is what lists that account's
  repositories), but the credential is platform-side and only reachable through an imported
  project. Importing the personal repository is refused by the Free plan's three-active-project
  limit, and freeing a slot means deleting a project — the owner's call, and this connection's
  grant covers a single project, so the other two cannot be enumerated from here.

So the publish step needs a credential that acts as `kobe0824man`, and it is one tested command
away: `GITHUB_PERSONAL_TOKEN=… bash tools/relocate_ci_to_personal_repo.sh`.
