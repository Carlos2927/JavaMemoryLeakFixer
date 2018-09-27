package com.carlos2927.java.memoryleakfixer;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class JavaMemoryLeakFixer {
    static final Map<Class,Watchable> ClassWatchers = new ConcurrentHashMap<>();
    public static void startWatchJavaMemory(){
        InnerClassHelper.initLoopThread();
        if(AppEnv.IsInAndroidPlatform){
            // If In Android Platform Class Watcher For AndroidPlatformMemoryWatcher Library
            try {
                Field field = JavaReflectUtils.getFieldWithoutCache("com.carlos2927.java_memory_leak_fixer_android_extension.AndroidPlatformMemoryWatcher","AndroidPlatformCache");
                Map<Class,Watchable> map = (Map<Class, Watchable>) field.get(null);
                ClassWatchers.putAll(map);
            }catch (Exception e){
                Log.w("JavaMemoryLeakFixer","In Android Platform,You Need Add Library:AndroidPlatformMemoryWatcher(https://github.com/Carlos2927/AndroidPlatformMemoryWatcher)");
            }
        }
    }

    public static void addWatchingClass(Class cls,Watchable watchable){
        ClassWatchers.put(cls,watchable);
    }

    public static void notifyWatcherTryToFixMemoryLeak(){
        InnerClassHelper.tryToWatch();
    }

    public static void removeWatchingClass(Class cls){
        ClassWatchers.remove(cls);
    }


    public static void stopWatchJavaMemory(){
        InnerClassHelper.release();
        ClassWatchers.clear();
    }
}
