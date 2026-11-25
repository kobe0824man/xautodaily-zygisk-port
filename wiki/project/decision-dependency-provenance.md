---
title: Verify dependency provenance in CI
type: decision
scope: project
---

Native and API dependencies are pinned and checked by scripts rather than trusted:

- `source/zygisk/tests/verify_xposed_api82.py` - API 82 native declarations vs. the JNI table and
  vs. the vendored tree.
- `source/zygisk/tests/verify_openssl_archives.py` - the four OpenSSL 1.1.1t static archives
  against their upstream Git blob IDs and ELF ABI (see `source/openssl/BUILD-PROVENANCE.md`).
- `source/zygisk/build-zygisk.sh` - refuses a missing payload, an unknown payload kind, an
  `original` payload whose SHA-256 does not match the uploaded APK, a payload that lacks the
  Zygisk bridge classes, and a missing `SUPPORT_APK`.

**Why**: the port's failure modes are runtime crashes inside a hooked app, where a wrong blob or a
mislabelled payload is invisible until a device test.
