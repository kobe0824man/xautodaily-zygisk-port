plugins {
    alias(libs.plugins.androidLibrary)
}

// Compile-only stubs for the hidden Android framework APIs that the upstream Xposed API 82
// sources reference (ActivityThread, LoadedApk, ServiceManager, XmlUtils, ...).
//
// This module is consumed with `compileOnly`, so none of these classes reach the APK. The real
// framework classes are provided by the platform at runtime.

android {
    namespace = "de.robv.android.xposed.stubs"
    compileSdk = libs.versions.compileSdk.get().toInt()
    buildToolsVersion = libs.versions.buildTool.get()

    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()
    }

    compileOptions {
        sourceCompatibility(JavaVersion.VERSION_21)
        targetCompatibility(JavaVersion.VERSION_21)
    }

    lint {
        // These stubs intentionally live in framework packages.
        disable += setOf("InvalidPackage")
    }
}
