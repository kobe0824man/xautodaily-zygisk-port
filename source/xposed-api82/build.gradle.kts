plugins {
    alias(libs.plugins.androidLibrary)
}

// Runtime implementation of the legacy Xposed API 82 used by the Zygisk payload.
//
// The published `de.robv.android.xposed:api:82` artifact is a compile-time stub: every method
// body is `throw new RuntimeException("Stub!")` and only `getXposedVersion` is left native.
// LSPosed hides that stub behind the framework's own `de.robv.android.xposed.*` classes, but the
// Zygisk payload has no framework, so the stub would be the implementation and every hook call
// would fail at runtime. This module packages the upstream API 82 implementation instead; the
// Zygisk JNI bridge registers the native entry points on it.

android {
    namespace = "de.robv.android.xposed.api82"
    compileSdk = libs.versions.compileSdk.get().toInt()
    buildToolsVersion = libs.versions.buildTool.get()

    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()
        consumerProguardFiles("consumer-rules.pro")
    }

    compileOptions {
        sourceCompatibility(JavaVersion.VERSION_21)
        targetCompatibility(JavaVersion.VERSION_21)
    }

    lint {
        // The vendored API 82 sources are upstream code and are not ours to lint.
        disable += setOf("InvalidPackage", "JavadocMethod", "JavadocType")
    }
}

dependencies {
    // Hidden framework APIs referenced by the upstream sources. Compile-only: the stubs are never
    // packaged, the platform provides the real classes at runtime.
    compileOnly(project(":xposed-api82-stubs"))
}
