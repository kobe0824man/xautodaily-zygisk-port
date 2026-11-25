package me.teble.xposed.autodaily.zygisk;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

import org.junit.Test;

public class HookRuntimeTest {
    public static final class Sample {
        final int seed;

        public Sample(int seed) { this.seed = seed; }
        public int add(int value) { return seed + value; }
        public static int multiply(int value) { return value * 2; }
    }

    public static int originalAdd(Sample receiver, int value) {
        return receiver.seed + value + 100;
    }

    @Test
    public void invokesLsPlantBackupWithExplicitReceiver() throws Exception {
        Method target = Sample.class.getDeclaredMethod("add", int.class);
        Method backup = HookRuntimeTest.class.getDeclaredMethod("originalAdd", Sample.class, int.class);
        Sample receiver = new Sample(7);
        HookRuntime.registerBackup(target, backup);

        Object result = HookRuntime.invokeOriginal(target, receiver, new Object[]{5});

        assertEquals(112, result);
        assertNotNull(HookRuntime.hasBackup(target) ? Boolean.TRUE : null);
    }

    @Test
    public void fallsBackToReflectionForUnhookedStaticMethods() throws Exception {
        Method target = Sample.class.getDeclaredMethod("multiply", int.class);
        Object result = HookRuntime.invokeOriginal(target, null, new Object[]{9});
        assertEquals(18, result);
    }

    @Test
    public void fallsBackToReflectionForConstructors() throws Exception {
        Constructor<Sample> constructor = Sample.class.getConstructor(int.class);
        Sample result = (Sample) HookRuntime.invokeOriginal(constructor, null, new Object[]{23});
        assertEquals(23, result.seed);
    }
}
