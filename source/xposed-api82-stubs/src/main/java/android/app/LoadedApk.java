package android.app;

import android.content.pm.ApplicationInfo;

/**
 * Compile-only stub for a hidden Android framework API. Never packaged: the platform provides the
 * real class at runtime.
 */
public class LoadedApk {
    public String getResDir() {
        throw new UnsupportedOperationException("stub");
    }

    public ClassLoader getClassLoader() {
        throw new UnsupportedOperationException("stub");
    }

    public String getPackageName() {
        throw new UnsupportedOperationException("stub");
    }

    public ApplicationInfo getApplicationInfo() {
        throw new UnsupportedOperationException("stub");
    }
}
