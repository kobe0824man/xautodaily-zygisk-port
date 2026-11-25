package external.org.apache.commons.lang3;

/**
 * Minimal stand-in for the shaded Apache Commons Lang classes that the upstream Xposed API 82
 * sources use. Compile-only: the platform does not provide these, and the upstream code only needs
 * class lookup and assignability checks.
 */
public class ClassUtils {
    public static Class<?> getClass(ClassLoader loader, String className, boolean initialize)
            throws ClassNotFoundException {
        return Class.forName(className, initialize, loader);
    }

    public static boolean isAssignable(Class<?>[] cls, Class<?>[] toClass, boolean autoboxing) {
        if (toClass == null) {
            return cls == null;
        }
        if (cls == null) {
            return toClass.length == 0;
        }
        if (cls.length != toClass.length) {
            return false;
        }
        for (int index = 0; index < cls.length; index++) {
            if (!isAssignable(cls[index], toClass[index], autoboxing)) {
                return false;
            }
        }
        return true;
    }

    private static boolean isAssignable(Class<?> from, Class<?> to, boolean autoboxing) {
        if (from == null || to == null) {
            return from == to;
        }
        if (from.equals(to)) {
            return true;
        }
        if (to.isAssignableFrom(from)) {
            return true;
        }
        if (!autoboxing) {
            return false;
        }
        return box(from).equals(box(to)) || box(to).equals(box(from));
    }

    private static Class<?> box(Class<?> type) {
        if (!type.isPrimitive()) {
            return type;
        }
        if (type == boolean.class) return Boolean.class;
        if (type == byte.class) return Byte.class;
        if (type == char.class) return Character.class;
        if (type == short.class) return Short.class;
        if (type == int.class) return Integer.class;
        if (type == long.class) return Long.class;
        if (type == float.class) return Float.class;
        if (type == double.class) return Double.class;
        return type;
    }
}
