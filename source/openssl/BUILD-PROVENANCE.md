# OpenSSL static library provenance

The Android OpenSSL headers and static libraries used by the application are pinned to
[LuckyPray/openssl-android commit 11794e5c6fd304b6d434acddd195b40e78e7603a ](https://github.com/LuckyPray/openssl-android/commit/11794e5c6fd304b6d434acddd195b40e78e7603a)
(Build OpenSSL 1.1.1t). The checked-in include/openssl/opensslv.h Git blob matches
that revision exactly. The four static archives below were fetched from that same commit
and verified against their upstream Git blob object IDs:

| ABI | Archive | Bytes | Git blob SHA-1 |
|---|---|---:|---|
| arm64-v8a | libcrypto.a | 5,160,398 | a85f2612ab8cf3d1617fca10a439753939239357 |
| arm64-v8a | libssl.a | 992,498 | 411607b9c1f00a7fd6c5e1b264b5b6ec328b20ee |
| armeabi-v7a | libcrypto.a | 3,857,268 | 1ea56d45318dd63486929e1cbffc1f367ef9aa32 |
| armeabi-v7a | libssl.a | 717,370 | 00509224b33a1826eda6bce71512a59666354578 |

source/zygisk/tests/verify_openssl_archives.py rechecks each Git blob hash and the
ELF class/machine in an archive member. The source tree previously carried the matching
headers but omitted lib/, which made the app CMake imported-library paths unresolved.
