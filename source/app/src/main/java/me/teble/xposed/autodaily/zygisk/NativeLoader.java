package me.teble.xposed.autodaily.zygisk;

import android.util.Log;

/** Loads the module's private JNI bridge from the payload APK extraction directory. */
public final class NativeLoader {
    private static final String TAG = "XAutoDaily-Zygisk";

    private NativeLoader() {}

    public static void load(String[] absolutePaths) {
        try {
            Class<?> bypass = Class.forName("org.lsposed.hiddenapibypass.HiddenApiBypass");
            bypass.getMethod("addHiddenApiExemptions", String[].class)
                    .invoke(null, (Object) new String[]{""});
        } catch (Throwable error) {
            Log.w(TAG, "Hidden API exemption helper unavailable; continuing with platform access", error);
        }
        for (String path : absolutePaths) {
            System.load(path);
            Log.i(TAG, "Loaded native dependency: " + path);
        }
    }
}
