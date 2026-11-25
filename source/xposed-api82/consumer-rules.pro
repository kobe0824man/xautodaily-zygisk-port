# The Zygisk payload registers native entry points on these classes by their exact binary names,
# so neither the classes nor their native methods may be renamed or stripped.
-keep class de.robv.android.xposed.** { *; }
-keep class android.content.res.X** { *; }
-keep class android.app.AndroidAppHelper { *; }
-keepclasseswithmembernames class de.robv.android.xposed.XposedBridge {
    native <methods>;
}
