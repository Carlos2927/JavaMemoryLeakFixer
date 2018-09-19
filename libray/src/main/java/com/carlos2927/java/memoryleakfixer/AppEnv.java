package com.carlos2927.java.memoryleakfixer;

import android.os.Build;

public class AppEnv {
    /**
     * 检测是否在android环境
     */
    public static final boolean IsInAndroidPlatform = new MyCallable<Boolean>(){
        @Override
        public Boolean call() {
            try {
                Class.forName("android.system.Os");
                Class.forName("android.os.Process");
                // Minimum compatibility  Android 2.3
                return Build.VERSION.SDK_INT >= Build.VERSION_CODES.GINGERBREAD;
            }catch (Exception e){

            }
            return false;
        }
    }.call();

    public static final boolean HasAndroidSupportLibraryV4 = new MyCallable<Boolean>(){
        @Override
        public Boolean call() {
            try {
                Class.forName("android.support.v4.app.FragmentActivity");
                return IsInAndroidPlatform ;
            }catch (Exception e){
            }
            return false;
        }
    }.call();

    /**
     * 检测是否支持java8
     */
    public static final boolean IsSupportJava8 = new MyCallable<Boolean>(){
        @Override
        public Boolean call() {
            try {
                Class.forName("java.util.Optional");
                return true;
            }catch (Exception e){

            }
            return false;
        }
    }.call();

    public static final int LibVersionCode = 1;
    public static final String LibVersion = "v1.0";

    /**
     * InnerClassHelper.InnerClassTargetList列表无数据时循环检测线程休眠时间
     */
    static  int InnerClassHelperLoopCheckingThread_FindEmptyDuration = 100;
    static  int InnerClassHelperLoopCheckingThread_InitDelayDuration = InnerClassHelperLoopCheckingThread_FindEmptyDuration*3+100;

    static {
        Log.w("AppEnv",String.format("JavaMemoryLeakFixer( LibVersionCode: %d , LibVersion: %s , IsInAndroidPlatform: %s , HasAndroidSupportLibraryV4: %s , IsSupportJava8: %s )",
                LibVersionCode,LibVersion,IsInAndroidPlatform,HasAndroidSupportLibraryV4,IsSupportJava8));
    }

    public static void setInnerClassHelperLoopCheckingThreadFindEmptyListSleepDuration(int innerClassHelperLoopCheckingThreadFindEmptyListSleepDuration){
        InnerClassHelperLoopCheckingThread_FindEmptyDuration = innerClassHelperLoopCheckingThreadFindEmptyListSleepDuration;
        InnerClassHelperLoopCheckingThread_InitDelayDuration = InnerClassHelperLoopCheckingThread_FindEmptyDuration*3+100;
    }
}
