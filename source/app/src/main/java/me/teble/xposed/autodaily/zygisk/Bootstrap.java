package me.teble.xposed.autodaily.zygisk;

import android.content.pm.ApplicationInfo;
import android.os.Process;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import dalvik.system.DexClassLoader;

/** Initial payload entry. Called by the small Zygisk loader before ActivityThread.main(). */
public final class Bootstrap {
    private static final String TAG = "XAutoDaily-Zygisk";
    private static final String MAIN_HOOK = "me.teble.xposed.autodaily.hook.MainHook";
    private static final String BIND_HOOK = "me.teble.xposed.autodaily.zygisk.BindApplicationHook";
    private static final AtomicBoolean STARTED = new AtomicBoolean(false);
    private static final AtomicBoolean MODULE_INITIALIZED = new AtomicBoolean(false);
    private static volatile HostClassLoaderBridge hostBridge;

    private Bootstrap() {}

    /** Signature shared with the native Zygisk loader. Do not add host-loader arguments here:
     * ActivityThread/LoadedApk is not initialized yet in postAppSpecialize.
     */
    public static void start(String modulePath, String packageName, String processName) {
        if (!isSupportedPackage(packageName)) return;
        if (!STARTED.compareAndSet(false, true)) return;
        try {
            startInternal(modulePath, packageName, processName);
        } catch (Throwable error) {
            STARTED.set(false);
            Log.e(TAG, "Could not arm deferred XAutoDaily bootstrap for " + processName, error);
            if (error instanceof Error) throw (Error) error;
            throw new IllegalStateException("Unable to arm XAutoDaily Zygisk payload", error);
        }
    }

    private static boolean isSupportedPackage(String packageName) {
        return "com.tencent.mobileqq".equals(packageName) || "com.tencent.tim".equals(packageName);
    }

    private static void startInternal(String modulePath, String packageName, String processName)
            throws Throwable {
        File apk = new File(modulePath);
        if (!apk.isFile()) throw new IllegalArgumentException("Payload APK does not exist: " + modulePath);

        String abi = Process.is64Bit() ? "arm64-v8a" : "armeabi-v7a";
        File runtimeDir = new File(apk.getParentFile(),
                "xautodaily-zygisk-" + apk.length() + "-" + apk.lastModified());
        File optimizedDir = new File(runtimeDir, "dex");
        File nativeDir = new File(runtimeDir, "lib");
        ensureDirectory(optimizedDir, "dex cache");
        ensureDirectory(nativeDir, "native cache");

        extractNativeLibraries(apk, abi, nativeDir);
        copyExternalNativeLibraries(new File(apk.getParentFile(),
                "xautodaily-native-libs/" + abi), nativeDir);
        HostClassLoaderBridge bridge = new HostClassLoaderBridge(Bootstrap.class.getClassLoader());
        hostBridge = bridge;
        ModuleClassLoader moduleLoader = new ModuleClassLoader(
                apk.getAbsolutePath(), optimizedDir.getAbsolutePath(), nativeDir.getAbsolutePath(), bridge);

        List<String> libraries = new ArrayList<>();
        addIfPresent(libraries, new File(nativeDir, "libc++_shared.so"));
        requireAndAdd(libraries, new File(nativeDir, "liblsplant.so"));
        requireAndAdd(libraries, new File(nativeDir, "libdobby.so"));
        requireAndAdd(libraries, new File(nativeDir, "libxautodaily_hook.so"));
        loadNativeLibraries(moduleLoader, libraries.toArray(new String[0]));

        Class<?> bridgeClass = moduleLoader.loadClass("de.robv.android.xposed.XposedBridge");
        configureArtRuntime(bridgeClass);
        installBindApplicationHook(moduleLoader, bridge, modulePath, packageName, processName, bridgeClass);
        Log.i(TAG, "Deferred bootstrap armed for " + packageName + "/" + processName
                + "; waiting for ActivityThread.handleBindApplication");
    }

    private static void installBindApplicationHook(ClassLoader moduleLoader,
                                                   HostClassLoaderBridge bridge,
                                                   String modulePath, String packageName,
                                                   String processName, Class<?> xposedBridge)
            throws Throwable {
        ClassLoader bootLoader = ClassLoader.getSystemClassLoader();
        Class<?> activityThread = Class.forName("android.app.ActivityThread", false, bootLoader);
        Method bindMethod = null;
        for (Method method : activityThread.getDeclaredMethods()) {
            if ("handleBindApplication".equals(method.getName())
                    && method.getParameterTypes().length == 1) {
                bindMethod = method;
                break;
            }
        }
        if (bindMethod == null) {
            throw new NoSuchMethodException("ActivityThread.handleBindApplication(AppBindData)");
        }
        bindMethod.setAccessible(true);

        Class<?> callbackBase = moduleLoader.loadClass("de.robv.android.xposed.XC_MethodHook");
        Class<?> callbackClass = moduleLoader.loadClass(BIND_HOOK);
        Constructor<?> callbackConstructor = callbackClass.getConstructor(
                HostClassLoaderBridge.class, ClassLoader.class,
                String.class, String.class, String.class);
        Object callback = callbackConstructor.newInstance(
                bridge, moduleLoader, modulePath, packageName, processName);
        Method hookMethod = xposedBridge.getMethod("hookMethod", Member.class, callbackBase);
        Object unhook = invokeUnwrapped(hookMethod, null, bindMethod, callback);
        callbackClass.getMethod("setUnhook", Object.class).invoke(callback, unhook);
    }

    /** Called by BindApplicationHook after AppBindData.info has exposed the actual LoadedApk loader. */
    public static void initializeModule(ClassLoader moduleLoader, HostClassLoaderBridge bridge,
                                        String modulePath, String packageName, String processName,
                                        ClassLoader hostClassLoader, ApplicationInfo appInfo)
            throws Throwable {
        if (!MODULE_INITIALIZED.compareAndSet(false, true)) return;
        try {
            if (hostClassLoader == null) throw new IllegalArgumentException("Host LoadedApk ClassLoader is null");
            bridge.setHostClassLoader(hostClassLoader);

            Class<?> bridgeClass = moduleLoader.loadClass("de.robv.android.xposed.XposedBridge");
            setStaticBoolean(bridgeClass, "isZygote", false);
            setStaticInt(bridgeClass, "XPOSED_BRIDGE_VERSION", 82);

            Class<?> mainHookClass = moduleLoader.loadClass(MAIN_HOOK);
            Constructor<?> mainConstructor = mainHookClass.getDeclaredConstructor();
            mainConstructor.setAccessible(true);
            Object module = mainConstructor.newInstance();

            Object startupParam = newStartupParam(moduleLoader, modulePath);
            Method initZygote = mainHookClass.getMethod(
                    "initZygote", moduleLoader.loadClass(
                            "de.robv.android.xposed.IXposedHookZygoteInit$StartupParam"));
            invokeUnwrapped(initZygote, module, startupParam);

            Object loadPackageParam = newLoadPackageParam(
                    moduleLoader, hostClassLoader, packageName, processName, appInfo);
            Class<?> loadParamClass = moduleLoader.loadClass(
                    "de.robv.android.xposed.callbacks.XC_LoadPackage$LoadPackageParam");
            Method handleLoadPackage = mainHookClass.getMethod("handleLoadPackage", loadParamClass);
            invokeUnwrapped(handleLoadPackage, module, loadPackageParam);

            Log.i(TAG, "Initialized " + packageName + "/" + processName
                    + " with LoadedApk loader " + hostClassLoader);
        } catch (Throwable error) {
            MODULE_INITIALIZED.set(false);
            Log.e(TAG, "Xposed-compatible module initialization failed for " + processName, error);
            throw error;
        }
    }

    /** Keep ModuleClassLoader's host delegation synchronized with Tinker loader replacement. */
    public static void updateHostClassLoader(ClassLoader loader) {
        HostClassLoaderBridge bridge = hostBridge;
        if (bridge != null && loader != null) bridge.setHostClassLoader(loader);
    }

    private static void configureArtRuntime(Class<?> bridgeClass) throws Exception {
        setStaticInt(bridgeClass, "runtime", 2); // API 82 RUNTIME_ART
    }

    private static void setStaticInt(Class<?> type, String name, int value) throws Exception {
        Field field = type.getDeclaredField(name);
        field.setAccessible(true);
        field.setInt(null, value);
    }

    private static void setStaticBoolean(Class<?> type, String name, boolean value) throws Exception {
        Field field = type.getDeclaredField(name);
        field.setAccessible(true);
        field.setBoolean(null, value);
    }

    private static Object newStartupParam(ClassLoader loader, String modulePath) throws Exception {
        Class<?> startupClass = loader.loadClass(
                "de.robv.android.xposed.IXposedHookZygoteInit$StartupParam");
        Constructor<?> constructor = startupClass.getDeclaredConstructor();
        constructor.setAccessible(true);
        Object startup = constructor.newInstance();
        startupClass.getField("modulePath").set(startup, modulePath);
        startupClass.getField("startsSystemServer").setBoolean(startup, false);
        return startup;
    }

    private static Object newLoadPackageParam(ClassLoader loader, ClassLoader hostClassLoader,
                                              String packageName, String processName,
                                              ApplicationInfo appInfo) throws Exception {
        Class<?> setClass = loader.loadClass(
                "de.robv.android.xposed.XposedBridge$CopyOnWriteSortedSet");
        Constructor<?> setConstructor = setClass.getDeclaredConstructor();
        setConstructor.setAccessible(true);
        Object callbacks = setConstructor.newInstance();

        Class<?> paramClass = loader.loadClass(
                "de.robv.android.xposed.callbacks.XC_LoadPackage$LoadPackageParam");
        Constructor<?> constructor = paramClass.getDeclaredConstructor(setClass);
        constructor.setAccessible(true);
        Object param = constructor.newInstance(callbacks);
        paramClass.getField("packageName").set(param, packageName);
        paramClass.getField("processName").set(param, processName);
        paramClass.getField("classLoader").set(param, hostClassLoader);
        paramClass.getField("appInfo").set(param, appInfo);
        paramClass.getField("isFirstApplication").setBoolean(param, packageName.equals(processName));
        return param;
    }

    private static void loadNativeLibraries(ClassLoader moduleLoader, String[] paths) throws Throwable {
        Class<?> nativeLoader = moduleLoader.loadClass("me.teble.xposed.autodaily.zygisk.NativeLoader");
        Method load = nativeLoader.getMethod("load", String[].class);
        invokeUnwrapped(load, null, (Object) paths);
    }

    private static void ensureDirectory(File directory, String label) {
        if (!directory.isDirectory() && !directory.mkdirs()) {
            throw new IllegalStateException("Cannot create " + label + ": " + directory);
        }
    }

    private static void extractNativeLibraries(File apk, String abi, File outputDir) throws Exception {
        String prefix = "lib/" + abi + "/";
        try (ZipFile zip = new ZipFile(apk)) {
            java.util.Enumeration<? extends ZipEntry> entries = zip.entries();
            byte[] buffer = new byte[32 * 1024];
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                String name = entry.getName();
                if (entry.isDirectory() || !name.startsWith(prefix) || !name.endsWith(".so")) continue;
                String fileName = name.substring(prefix.length());
                if (fileName.isEmpty() || fileName.indexOf('/') >= 0 || fileName.contains("..")) continue;
                File target = new File(outputDir, fileName);
                if (target.isFile() && target.length() == entry.getSize()) continue;
                File temporary = new File(outputDir, fileName + ".tmp");
                try (InputStream input = zip.getInputStream(entry);
                     FileOutputStream output = new FileOutputStream(temporary)) {
                    int count;
                    while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
                    output.flush();
                    output.getFD().sync();
                }
                if (target.exists() && !target.delete()) {
                    throw new IllegalStateException("Cannot replace native library: " + target);
                }
                if (!temporary.renameTo(target)) {
                    throw new IllegalStateException("Cannot install native library: " + target);
                }
                target.setReadable(true, false);
                target.setExecutable(true, false);
            }
        }
    }

    /** The original LSPosed APK predates this port's LSPlant/Dobby bridge. The Zygisk
     * installer therefore stages those support libraries beside the exact, unmodified APK.
     */
    private static void copyExternalNativeLibraries(File sourceDir, File outputDir) throws Exception {
        String[] required = {"liblsplant.so", "libdobby.so", "libxautodaily_hook.so"};
        byte[] buffer = new byte[32 * 1024];
        for (String name : required) {
            File source = new File(sourceDir, name);
            File target = new File(outputDir, name);
            if (target.isFile() && target.length() > 0) continue;
            if (!source.isFile() || source.length() <= 0) {
                throw new IllegalStateException("Required native library missing from APK and module: " + name);
            }
            File temporary = new File(outputDir, name + ".tmp");
            try (InputStream input = new java.io.FileInputStream(source);
                 FileOutputStream output = new FileOutputStream(temporary)) {
                int count;
                while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
                output.flush();
                output.getFD().sync();
            }
            if (target.exists() && !target.delete()) {
                throw new IllegalStateException("Cannot replace native library: " + target);
            }
            if (!temporary.renameTo(target)) {
                throw new IllegalStateException("Cannot install native library: " + target);
            }
            target.setReadable(true, false);
            target.setExecutable(true, false);
        }
    }

    private static void addIfPresent(List<String> paths, File file) {
        if (file.isFile()) paths.add(file.getAbsolutePath());
    }

    private static void requireAndAdd(List<String> paths, File file) {
        if (!file.isFile()) throw new IllegalStateException("Required native library missing: " + file);
        paths.add(file.getAbsolutePath());
    }

    private static Object invokeUnwrapped(Method method, Object receiver, Object... args) throws Throwable {
        try {
            return method.invoke(receiver, args);
        } catch (InvocationTargetException error) {
            throw error.getCause() == null ? error : error.getCause();
        }
    }

    /** Child-first for payload classes, with a late-bound parent for host QQ/TIM classes. */
    private static final class ModuleClassLoader extends DexClassLoader {
        ModuleClassLoader(String dexPath, String optimizedDirectory, String librarySearchPath,
                          ClassLoader parent) {
            super(dexPath, optimizedDirectory, librarySearchPath, parent);
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            synchronized (this) {
                Class<?> loaded = findLoadedClass(name);
                if (loaded == null) {
                    if (isParentFirst(name)) {
                        loaded = super.loadClass(name, false);
                    } else {
                        try {
                            loaded = findClass(name);
                        } catch (ClassNotFoundException notInPayload) {
                            loaded = super.loadClass(name, false);
                        }
                    }
                }
                if (resolve) resolveClass(loaded);
                return loaded;
            }
        }

        private static boolean isParentFirst(String name) {
            if (name.equals(Bootstrap.class.getName())
                    || name.equals(HostClassLoaderBridge.class.getName())) return true;
            // API 82 supplies these Android-package classes inside the payload APK.
            if (name.equals("android.app.AndroidAppHelper") || name.startsWith("android.content.res.X")) {
                return false;
            }
            return name.startsWith("java.") || name.startsWith("javax.")
                    || name.startsWith("android.") || name.startsWith("dalvik.")
                    || name.startsWith("libcore.") || name.startsWith("sun.")
                    || name.startsWith("org.w3c.") || name.startsWith("org.xml.")
                    || name.startsWith("org.json.") || name.startsWith("com.android.")
                    || name.equals("com.tencent.mobileqq") || name.startsWith("com.tencent.mobileqq.")
                    || name.equals("com.tencent.tim") || name.startsWith("com.tencent.tim.")
                    || name.startsWith("com.tencent.qphone.")
                    || name.startsWith("com.tencent.common.")
                    || name.startsWith("com.tencent.qqnt.")
                    || name.startsWith("mqq.") || name.startsWith("oicq.")
                    || name.startsWith("tencent.im.");
        }
    }
}
