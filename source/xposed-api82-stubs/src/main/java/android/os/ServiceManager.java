package android.os;

/**
 * Compile-only stub for a hidden Android framework API. Never packaged: the platform provides the
 * real class at runtime.
 */
public class ServiceManager {
    public static IBinder getService(String name) {
        throw new UnsupportedOperationException("stub");
    }
}
