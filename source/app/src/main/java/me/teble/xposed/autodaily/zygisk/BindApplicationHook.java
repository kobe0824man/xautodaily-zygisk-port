package me.teble.xposed.autodaily.zygisk;

import android.app.ApplicationInfo;
import android.util.Log;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicBoolean;

import de.robv.android.xposed.XC_MethodHook;

/** Runs at the start of ActivityThread.handleBindApplication, after LoadedApk exists but before
 * the target Application is created. This is the earliest reliable point at which its real
 * ClassLoader can be used without racing ActivityThread.main().
 */
public final class BindApplicationHook extends XC_MethodHook {
    private static final String TAG = "XAutoDaily-Zygisk";
    private final HostClassLoaderBridge hostBridge;
    private final ClassLoader moduleLoader;
    private final String modulePath;
    private final String packageName;
    private final String processName;
    private final AtomicBoolean entered = new AtomicBoolean(false);
    private volatile Object unhook;

    public BindApplicationHook(HostClassLoaderBridge hostBridge, ClassLoader moduleLoader,
                               String modulePath, String packageName, String processName) {
        super(XC_MethodHook.PRIORITY_HIGHEST);
        this.hostBridge = hostBridge;
        this.moduleLoader = moduleLoader;
        this.modulePath = modulePath;
        this.packageName = packageName;
        this.processName = processName;
    }

    public void setUnhook(Object unhook) {
        this.unhook = unhook;
    }

    @Override
    protected void beforeHookedMethod(MethodHookParam param) {
        if (!entered.compareAndSet(false, true)) return;
        try {
            Object bindData = param.args != null && param.args.length > 0 ? param.args[0] : null;
            if (bindData == null) throw new IllegalStateException("ActivityThread bind data is missing");

            Object loadedApk = readField(bindData, "info");
            if (loadedApk == null) throw new IllegalStateException("ActivityThread.AppBindData.info is null");
            Method getClassLoader = loadedApk.getClass().getDeclaredMethod("getClassLoader");
            getClassLoader.setAccessible(true);
            ClassLoader hostLoader = (ClassLoader) getClassLoader.invoke(loadedApk);
            if (hostLoader == null) throw new IllegalStateException("LoadedApk returned a null ClassLoader");

            ApplicationInfo appInfo = (ApplicationInfo) readField(bindData, "appInfo");
            if (appInfo != null && appInfo.packageName != null
                    && !packageName.equals(appInfo.packageName)) {
                Log.w(TAG, "Ignoring bind for unexpected package " + appInfo.packageName);
                return;
            }

            hostBridge.setHostClassLoader(hostLoader);
            Bootstrap.initializeModule(moduleLoader, hostBridge, modulePath,
                    packageName, processName, hostLoader, appInfo);
        } catch (Throwable error) {
            Log.e(TAG, "Deferred XAutoDaily bootstrap failed before Application creation", unwrap(error));
        } finally {
            removeCallback();
        }
    }

    private void removeCallback() {
        Object current = unhook;
        if (current == null) return;
        try {
            Method method = current.getClass().getMethod("unhook");
            method.invoke(current);
        } catch (Throwable error) {
            Log.w(TAG, "Could not remove the one-shot bind callback", unwrap(error));
        }
    }

    private static Object readField(Object instance, String name) throws Exception {
        for (Class<?> type = instance.getClass(); type != null; type = type.getSuperclass()) {
            try {
                Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                return field.get(instance);
            } catch (NoSuchFieldException ignored) {
                // Continue through the class hierarchy.
            }
        }
        throw new NoSuchFieldException(instance.getClass().getName() + "." + name);
    }

    private static Throwable unwrap(Throwable error) {
        while ((error instanceof java.lang.reflect.InvocationTargetException)
                && ((java.lang.reflect.InvocationTargetException) error).getCause() != null) {
            error = ((java.lang.reflect.InvocationTargetException) error).getCause();
        }
        return error;
    }
}
