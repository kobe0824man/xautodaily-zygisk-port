@file:JvmName("ModuleInfo")
package me.teble.xposed.autodaily.hook.base

import me.teble.xposed.autodaily.hook.MainHook

lateinit var modulePath: String

val moduleClassLoader: ClassLoader = MainHook::class.java.classLoader!!
@Volatile
var moduleLoadInit = false
