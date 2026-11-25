package me.teble.xposed.autodaily.zygisk;

/**
 * Parent loader used by the payload DexClassLoader. The target LoadedApk loader does not exist
 * during Zygisk postAppSpecialize, so it is installed later from ActivityThread's bind callback.
 */
public final class HostClassLoaderBridge extends ClassLoader {
    private static final String BOOTSTRAP_CLASS = "me.teble.xposed.autodaily.zygisk.Bootstrap";
    private static final String BRIDGE_CLASS = "me.teble.xposed.autodaily.zygisk.HostClassLoaderBridge";
    private volatile ClassLoader hostClassLoader;

    public HostClassLoaderBridge(ClassLoader bootstrapClassLoader) {
        super(bootstrapClassLoader);
    }

    public void setHostClassLoader(ClassLoader loader) {
        if (loader == this) throw new IllegalArgumentException("Host loader cannot be the bridge itself");
        hostClassLoader = loader;
    }

    public ClassLoader getHostClassLoader() {
        return hostClassLoader;
    }

    @Override
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        synchronized (this) {
            Class<?> loaded = findLoadedClass(name);
            if (loaded == null) {
                // These two classes must remain the copies loaded by the initial payload loader;
                // the host application must never shadow the bridge used by BindApplicationHook.
                if (BOOTSTRAP_CLASS.equals(name) || BRIDGE_CLASS.equals(name)) {
                    loaded = super.loadClass(name, false);
                } else {
                    ClassLoader host = hostClassLoader;
                    if (host != null && host != this) {
                        try {
                            loaded = host.loadClass(name);
                        } catch (ClassNotFoundException ignored) {
                            // Fall through to the initial payload/system loader.
                        }
                    }
                    if (loaded == null) loaded = super.loadClass(name, false);
                }
            }
            if (resolve) resolveClass(loaded);
            return loaded;
        }
    }
}
