package me.teble.xposed.autodaily.zygisk;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

import org.junit.Before;
import org.junit.Test;

public class HookRuntimeTest {
    @Before
    public void clearRegistry() {
        HookRuntime.clearBackups();
    }

    public static final class Sample {
        int seed;

        public Sample(int seed) { this.seed = seed; }
        public int add(int value) { return seed + value; }
        public int backupAdd(int value) { return seed + value + 100; }
        public void backupConstructor(int value) { this.seed = value; }
        public static int multiply(int value) { return value * 2; }
        public static int backupMultiply(int value) { return value * 3; }
    }

    @Test
    public void invokesLsPlantBackupWithExplicitReceiver() throws Exception {
        Method target = Sample.class.getDeclaredMethod("add", int.class);
        Method backup = Sample.class.getDeclaredMethod("backupAdd", int.class);
        Sample receiver = new Sample(7);
        HookRuntime.registerBackup(target, backup);

        Object result = HookRuntime.invokeOriginal(target, receiver, new Object[]{5});

        assertEquals(112, result);
        assertTrue(HookRuntime.hasBackup(target));
    }

    @Test
    public void invokesConstructorBackupOnTheConstructedReceiver() throws Exception {
        Constructor<Sample> target = Sample.class.getConstructor(int.class);
        Method backup = Sample.class.getDeclaredMethod("backupConstructor", int.class);
        Sample receiver = new Sample(1);
        HookRuntime.registerBackup(target, backup);

        Object result = HookRuntime.invokeOriginal(target, receiver, new Object[]{23});

        assertEquals(null, result);
        assertEquals(23, receiver.seed);
    }

    @Test
    public void invokesLsPlantBackupForStaticMethods() throws Exception {
        Method target = Sample.class.getDeclaredMethod("multiply", int.class);
        Method backup = Sample.class.getDeclaredMethod("backupMultiply", int.class);
        HookRuntime.registerBackup(target, backup);

        Object result = HookRuntime.invokeOriginal(target, null, new Object[]{7});

        assertEquals(21, result);
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
