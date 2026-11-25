package me.teble.xposed.autodaily.zygisk;

import static org.junit.Assert.assertSame;
import static org.junit.Assert.fail;

import org.junit.Test;

public class HostClassLoaderBridgeTest {
    private static final class HostOnlyType {}

    private static final String BRIDGE_CLASS = "me.teble.xposed.autodaily.zygisk.HostClassLoaderBridge";
    private static final String BOOTSTRAP_CLASS = "me.teble.xposed.autodaily.zygisk.Bootstrap";

    @Test
    public void hostLoaderCanBeInstalledAfterBootstrapStarts() throws Exception {
        HostClassLoaderBridge bridge = new HostClassLoaderBridge(new ClassLoader(null) {});
        try {
            bridge.loadClass(HostOnlyType.class.getName());
            fail("The bootstrap parent must not resolve an app-only class before bind");
        } catch (ClassNotFoundException expected) {
            // The host LoadedApk loader is deliberately not available yet.
        }

        bridge.setHostClassLoader(HostOnlyType.class.getClassLoader());

        assertSame(HostOnlyType.class, bridge.loadClass(HostOnlyType.class.getName()));
    }

    @Test
    public void hostLoaderCannotShadowTheBridgeOrBootstrapClasses() throws Exception {
        HostClassLoaderBridge bridge =
                new HostClassLoaderBridge(HostClassLoaderBridge.class.getClassLoader());
        bridge.setHostClassLoader(new ShadowingClassLoader());

        assertSame(HostClassLoaderBridge.class, bridge.loadClass(BRIDGE_CLASS));
        assertSame(Bootstrap.class, bridge.loadClass(BOOTSTRAP_CLASS));
    }

    /** Stands in for a host application that ships classes with the payload's own names. */
    private static final class ShadowingClassLoader extends ClassLoader {
        ShadowingClassLoader() {
            super(null);
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if (BRIDGE_CLASS.equals(name) || BOOTSTRAP_CLASS.equals(name)) return HostOnlyType.class;
            return super.loadClass(name, resolve);
        }
    }
}
