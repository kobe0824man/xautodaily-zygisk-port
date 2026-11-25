package me.teble.xposed.autodaily.zygisk;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;

/** Adapts LSPlant's Object[] callback to the legacy XposedBridge callback dispatcher. */
public final class XposedHooker {
    private static final Method HANDLE_HOOKED_METHOD = findDispatcher();

    private final Member target;
    private final Object additionalInfo;

    public XposedHooker(Member target, Object additionalInfo) {
        this.target = target;
        this.additionalInfo = additionalInfo;
    }

    public Object dispatch(Object[] lsplantArguments) throws Throwable {
        Object thisObject = null;
        Object[] arguments = lsplantArguments == null ? new Object[0] : lsplantArguments;
        boolean isStatic = target instanceof Method && Modifier.isStatic(target.getModifiers());
        if (!isStatic) {
            if (arguments.length == 0) throw new IllegalStateException("LSPlant omitted receiver for " + target);
            thisObject = arguments[0];
            arguments = Arrays.copyOfRange(arguments, 1, arguments.length);
        }
        try {
            return HANDLE_HOOKED_METHOD.invoke(null, target, 0, additionalInfo, thisObject, arguments);
        } catch (InvocationTargetException error) {
            throw error.getCause() == null ? error : error.getCause();
        }
    }

    private static Method findDispatcher() {
        try {
            ClassLoader loader = XposedHooker.class.getClassLoader();
            Class<?> bridge = Class.forName("de.robv.android.xposed.XposedBridge", true, loader);
            Method method = bridge.getDeclaredMethod("handleHookedMethod", Member.class, int.class,
                    Object.class, Object.class, Object[].class);
            method.setAccessible(true);
            return method;
        } catch (Exception error) {
            throw new ExceptionInInitializerError(error);
        }
    }
}
