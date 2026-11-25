package me.teble.xposed.autodaily.zygisk;

/**
 * Parent loader used by the payload DexClassLoader. The target LoadedApk loader does not exist
 * during Zygisk postAppSpecialize, so it is installed later from ActivityThread's bind callback.
 */
public final class HostClassLoaderBridge extends ClassLoader {
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
                ClassLoader host = hostClassLoader;
                if (host != null && host != this) {
                    try {
                        loaded = host.loadClass(name);
                    } catch (ClassNotFoundException ignored) {
                        // The bootstrap loader still owns the payload bootstrap classes.
                    }
                }
                if (loaded == null) loaded = super.loadClass(name, false);
            }
            if (resolve) resolveClass(loaded);
            return loaded;
        }
    }
}
