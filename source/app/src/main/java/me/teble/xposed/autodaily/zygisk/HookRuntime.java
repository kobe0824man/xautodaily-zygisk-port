package me.teble.xposed.autodaily.zygisk;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/** Java-side backup-method registry used by the API-82 invokeOriginalMethod native entry. */
public final class HookRuntime {
    private static final ConcurrentMap<Member, Method> BACKUPS = new ConcurrentHashMap<>();

    private HookRuntime() {}

    public static void registerBackup(Member target, Method backup) {
        if (target == null || backup == null) throw new IllegalArgumentException("target/backup must not be null");
        backup.setAccessible(true);
        BACKUPS.put(target, backup);
    }

    public static Object invokeOriginal(Member target, Object thisObject, Object[] arguments)
            throws IllegalAccessException, InvocationTargetException {
        if (target == null) throw new IllegalArgumentException("method must not be null");
        Object[] args = arguments == null ? new Object[0] : arguments;
        Method backup = BACKUPS.get(target);
        if (backup != null) {
            Object[] backupArgs = args;
            boolean isStatic = target instanceof Method && Modifier.isStatic(target.getModifiers());
            if (!isStatic) {
                backupArgs = new Object[args.length + 1];
                backupArgs[0] = thisObject;
                System.arraycopy(args, 0, backupArgs, 1, args.length);
            }
            backup.setAccessible(true);
            return backup.invoke(null, backupArgs);
        }
        if (target instanceof Method) {
            Method method = (Method) target;
            method.setAccessible(true);
            return method.invoke(thisObject, args);
        }
        if (target instanceof Constructor<?>) {
            Constructor<?> constructor = (Constructor<?>) target;
            constructor.setAccessible(true);
            return constructor.newInstance(args);
        }
        throw new IllegalArgumentException("Only methods and constructors are supported: " + target);
    }

    static boolean hasBackup(Member target) {
        return BACKUPS.containsKey(target);
    }
}
