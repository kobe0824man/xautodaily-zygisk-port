package me.teble.xposed.autodaily.zygisk;

import static org.junit.Assert.assertSame;
import static org.junit.Assert.fail;

import org.junit.Test;

public class HostClassLoaderBridgeTest {
    private static final class HostOnlyType {}

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
}
