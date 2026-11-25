---
title: CI cannot start any job for this organisation
type: question
scope: project
---

Every workflow run in `productos-agents/productos-xautodaily-zygisk-port-4f7949f5` fails in under
two seconds with `runner_id = 0`, no steps and an empty log. A deliberately trivial self-check
workflow (`echo` only, `.github/workflows/actions-selfcheck.yml`) fails identically, so the port's
workflow and sources are not the cause: GitHub Actions cannot allocate a runner for this
organisation at all. The organisation is on the free plan and its Actions usage for November 2026
is close to the included monthly minutes (private-repository minutes count double).

**What the repository owner has to do**: resolve the organisation's Actions billing or spending
limit so jobs can start, or run the CI workflow on a runner that is not metered by this
organisation.

**Until then**: source-level evidence (`source/zygisk/tests/verify_local_port.sh`) is the only
executable verification, and the full Gradle/JNI build remains unexecuted.
