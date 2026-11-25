package external.org.apache.commons.lang3.reflect;

/**
 * Minimal stand-in for the shaded Apache Commons Lang member utilities that the upstream Xposed
 * API 82 sources use to pick the most specific overload. Compile-only.
 */
public class MemberUtils {
    private static final int INCOMPATIBLE = Integer.MAX_VALUE;

    /**
     * Compares two candidate parameter lists against the actual argument types. A negative result
     * means {@code left} is the better match, a positive result means {@code right} is, and zero
     * means the two are equivalent.
     */
    public static int compareParameterTypes(Class<?>[] left, Class<?>[] right, Class<?>[] actual) {
        if (left == null || right == null) {
            return left == right ? 0 : (left == null ? -1 : 1);
        }
        if (left.length != right.length) {
            return left.length - right.length;
        }
        int result = 0;
        for (int index = 0; index < left.length; index++) {
            Class<?> actualType = actual != null && index < actual.length ? actual[index] : null;
            int leftScore = distance(left[index], actualType);
            int rightScore = distance(right[index], actualType);
            if (leftScore != rightScore) {
                result += leftScore < rightScore ? -1 : 1;
            }
        }
        return result;
    }

    /** Lower is better: an exact match beats a widening reference conversion. */
    private static int distance(Class<?> candidate, Class<?> actual) {
        if (candidate == null || actual == null) {
            return 1;
        }
        if (candidate.equals(actual)) {
            return 0;
        }
        if (actual.isPrimitive() || candidate.isPrimitive()) {
            Class<?> boxedCandidate = box(candidate);
            Class<?> boxedActual = box(actual);
            if (boxedCandidate.equals(boxedActual)) {
                return 1;
            }
            if (boxedActual.isAssignableFrom(boxedCandidate)) {
                return 2;
            }
            return INCOMPATIBLE;
        }
        if (actual.isAssignableFrom(candidate)) {
            return 1;
        }
        return INCOMPATIBLE;
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
