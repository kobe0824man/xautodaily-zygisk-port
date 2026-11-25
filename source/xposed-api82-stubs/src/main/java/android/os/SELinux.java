package android.os;

/**
 * Compile-only stub for a hidden Android framework API. Never packaged: the platform provides the
 * real class at runtime.
 */
public class SELinux {
    public static boolean isSELinuxEnforced() {
        throw new UnsupportedOperationException("stub");
    }

    public static boolean isSELinuxEnabled() {
        throw new UnsupportedOperationException("stub");
    }

    public static String getContext() {
        throw new UnsupportedOperationException("stub");
    }
}
