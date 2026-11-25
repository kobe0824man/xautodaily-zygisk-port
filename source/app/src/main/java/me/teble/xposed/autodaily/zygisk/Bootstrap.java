package me.teble.xposed.autodaily.zygisk;

import android.app.ApplicationInfo;
import android.os.Process;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import dalvik.system.DexClassLoader;

/** Entry point called by the Zygisk loader after the host process is specialized. */
public final class Bootstrap {
    private static final String TAG = "XAutoDaily-Zygisk";
    private static final String MAIN_HOOK = "me.teble.xposed.autodaily.hook.MainHook";
    private static final AtomicBoolean STARTED = new AtomicBoolean(false);

    private Bootstrap() {}

    /** Signature is part of the native loader contract; keep it stable. */
    public static void start(ClassLoader hostClassLoader, String modulePath,
                             String packageName, String processName) {
        if (!isSupportedPackage(packageName)) return;
        if (!STARTED.compareAndSet(false, true)) return;
        try {
            startInternal(hostClassLoader, modulePath, packageName, processName);
        } catch (Throwable error) {
            STARTED.set(false);
            Log.e(TAG, "Xposed-compatible bootstrap failed for " + processName, error);
            if (error instanceof Error) throw (Error) error;
            throw new IllegalStateException("Unable to initialize XAutoDaily Zygisk payload", error);
        }
    }

    private static boolean isSupportedPackage(String packageName) {
        return "com.tencent.mobileqq".equals(packageName) || "com.tencent.tim".equals(packageName);
    }

    private static void startInternal(ClassLoader hostClassLoader, String modulePath,
                                      String packageName, String processName) throws Throwable {
        if (hostClassLoader == null) hostClassLoader = ClassLoader.getSystemClassLoader();
        File apk = new File(modulePath);
        if (!apk.isFile()) throw new IllegalArgumentException("Payload APK does not exist: " + modulePath);

        String abi = Process.is64Bit() ? "arm64-v8a" : "armeabi-v7a";
        File runtimeDir = new File(apk.getParentFile(),
                "xautodaily-zygisk-" + apk.length() + "-" + apk.lastModified());
        File optimizedDir = new File(runtimeDir, "dex");
        File nativeDir = new File(runtimeDir, "lib");
        if (!optimizedDir.isDirectory() && !optimizedDir.mkdirs()) {
            throw new IllegalStateException("Cannot create dex cache: " + optimizedDir);
        }
        if (!nativeDir.isDirectory() && !nativeDir.mkdirs()) {
            throw new IllegalStateException("Cannot create native cache: " + nativeDir);
        }

        extractNativeLibraries(apk, abi, nativeDir);
        ModuleClassLoader moduleLoader = new ModuleClassLoader(
                apk.getAbsolutePath(), optimizedDir.getAbsolutePath(), nativeDir.getAbsolutePath(), hostClassLoader);

        List<String> libraries = new ArrayList<>();
        addIfPresent(libraries, new File(nativeDir, "libc++_shared.so"));
        requireAndAdd(libraries, new File(nativeDir, "liblsplant.so"));
        requireAndAdd(libraries, new File(nativeDir, "libdobby.so"));
        requireAndAdd(libraries, new File(nativeDir, "libxautodaily_hook.so"));
        loadNativeLibraries(moduleLoader, libraries.toArray(new String[0]));

        Class<?> bridgeClass = moduleLoader.loadClass("de.robv.android.xposed.XposedBridge");
        configureArtRuntime(bridgeClass);

        Class<?> mainHookClass = moduleLoader.loadClass(MAIN_HOOK);
        Constructor<?> mainConstructor = mainHookClass.getDeclaredConstructor();
        mainConstructor.setAccessible(true);
        Object module = mainConstructor.newInstance();

        Object startupParam = newStartupParam(moduleLoader, modulePath);
        Method initZygote = mainHookClass.getMethod(
                "initZygote", moduleLoader.loadClass("de.robv.android.xposed.IXposedHookZygoteInit$StartupParam"));
        invokeUnwrapped(initZygote, module, startupParam);

        Object loadPackageParam = newLoadPackageParam(
                moduleLoader, hostClassLoader, packageName, processName, getBoundApplicationInfo());
        Class<?> loadParamClass = moduleLoader.loadClass(
                "de.robv.android.xposed.callbacks.XC_LoadPackage$LoadPackageParam");
        Method handleLoadPackage = mainHookClass.getMethod("handleLoadPackage", loadParamClass);
        invokeUnwrapped(handleLoadPackage, module, loadPackageParam);

        Log.i(TAG, "Initialized " + packageName + "/" + processName
                + " with host loader " + hostClassLoader + " and module loader " + moduleLoader);
    }

    private static void loadNativeLibraries(ClassLoader moduleLoader, String[] paths) throws Throwable {
        Class<?> nativeLoader = moduleLoader.loadClass("me.teble.xposed.autodaily.zygisk.NativeLoader");
        Method load = nativeLoader.getMethod("load", String[].class);
        invokeUnwrapped(load, null, (Object) paths);
    }

    private static void configureArtRuntime(Class<?> bridgeClass) throws Exception {
        Field runtime = bridgeClass.getDeclaredField("runtime");
        Field artRuntime = bridgeClass.getDeclaredField("RUNTIME_ART");
        runtime.setAccessible(true);
        artRuntime.setAccessible(true);
        runtime.setInt(null, artRuntime.getInt(null));
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

    private static ApplicationInfo getBoundApplicationInfo() {
        try {
            Class<?> activityThread = Class.forName("android.app.ActivityThread");
            Method current = activityThread.getDeclaredMethod("currentActivityThread");
            current.setAccessible(true);
            Object thread = current.invoke(null);
            if (thread == null) return null;
            Field boundField = activityThread.getDeclaredField("mBoundApplication");
            boundField.setAccessible(true);
            Object bound = boundField.get(thread);
            if (bound == null) return null;
            Field appInfoField = bound.getClass().getDeclaredField("appInfo");
            appInfoField.setAccessible(true);
            return (ApplicationInfo) appInfoField.get(bound);
        } catch (Throwable error) {
            Log.w(TAG, "Could not read ActivityThread.AppBindData.appInfo", error);
            return null;
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

    /** Child-first for payload code; host QQ/TIM classes and Android framework remain parent-first. */
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
            // These Xposed compatibility classes are packaged in the module APK, not in the host framework.
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
