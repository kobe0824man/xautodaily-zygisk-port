# Wiki index

Catalog of everything durable this project has learned. Pages are flat kebab-case files under
`project/`, with YAML frontmatter (`title`, `type`, `scope: project`).

## Entities
- [XAutoDaily Zygisk port](project/entity-zygisk-port.md) — what the project is and where the work lives.

## Concepts
- [Zygisk port validation model](project/concept-port-validation.md) — what can be verified locally, on CI, and only on a device.
- [The native bridge is never compiled by the source-level suite](project/concept-native-bridge-api.md) — how the C++ bridge is pinned against the LSPlant and Dobby headers instead.

## Decisions
- [Package a working API 82 runtime, not the published stub](project/decision-api82-runtime.md) — the vendored `source/xposed-api82` module.
- [Match the JNI table to the API 82 surface exactly](project/decision-jni-table-exact.md) — 10 native methods, machine-checked.
- [Verify dependency provenance in CI](project/decision-dependency-provenance.md) — API 82 sources and OpenSSL archives are pinned and checked.
- [Run CI from the owner's personal GitHub account](project/decision-ci-personal-repository.md) — the organisation cannot allocate a runner; 34/34 runs failed with no runner and no steps.

## Sessions
- See `../memory/log.md` for the append-only journal.
