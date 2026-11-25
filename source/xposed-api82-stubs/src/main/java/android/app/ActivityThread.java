package android.app;

import android.content.pm.ApplicationInfo;
import android.content.res.CompatibilityInfo;

/**
 * Compile-only stub for a hidden Android framework API. Never packaged: the platform provides the
 * real class at runtime.
 */
public class ActivityThread {
    public static ActivityThread currentActivityThread() {
        throw new UnsupportedOperationException("stub");
    }

    public static String currentPackageName() {
        throw new UnsupportedOperationException("stub");
    }

    public static Application currentApplication() {
        throw new UnsupportedOperationException("stub");
    }

    public LoadedApk getPackageInfoNoCheck(ApplicationInfo appInfo, CompatibilityInfo compatInfo) {
        throw new UnsupportedOperationException("stub");
    }
}
