package android.content.pm;

import java.io.File;

/**
 * Compile-only stub for a hidden Android framework API. Never packaged: the platform provides the
 * real class at runtime.
 */
public class PackageParser {
    public static class PackageLite {
        public String packageName;
    }

    public static class PackageParserException extends Exception {
        public PackageParserException(String name) {
            super(name);
        }
    }

    public static PackageLite parsePackageLite(File file, int flags) throws PackageParserException {
        throw new UnsupportedOperationException("stub");
    }

    public static PackageLite parsePackageLite(String path, int flags) throws PackageParserException {
        throw new UnsupportedOperationException("stub");
    }
}
