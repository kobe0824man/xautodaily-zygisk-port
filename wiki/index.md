# Wiki index

Catalog of everything durable this project has learned. Pages are flat kebab-case files under
`project/`, with YAML frontmatter (`title`, `type`, `scope: project`).

## Entities
- [XAutoDaily Zygisk port](project/entity-zygisk-port.md) — what the project is and where the work lives.

## Concepts
- [Zygisk port validation model](project/concept-port-validation.md) — what can be verified locally, on CI, and only on a device.

## Decisions
- [Package a working API 82 runtime, not the published stub](project/decision-api82-runtime.md) — the vendored `source/xposed-api82` module.
- [Match the JNI table to the API 82 surface exactly](project/decision-jni-table-exact.md) — 10 native methods, machine-checked.
- [Verify dependency provenance in CI](project/decision-dependency-provenance.md) — API 82 sources and OpenSSL archives are pinned and checked.

## Open Questions
- [CI cannot start any job for this organisation](project/question-ci-org-blocked.md) — what the repository owner has to do.

## Sessions
- See `../memory/log.md` for the append-only journal.
