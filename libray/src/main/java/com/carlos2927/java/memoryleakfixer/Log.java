package com.carlos2927.java.memoryleakfixer;

public class Log {

    public static void d(String tag,String msg){
        if(AppEnv.IsInAndroidPlatform){
            android.util.Log.d(tag,msg);
        }else {
            JavaLog.print(tag,"Debug",msg);
        }
    }
    public static void i(String tag,String msg){
        if(AppEnv.IsInAndroidPlatform){
            android.util.Log.i(tag,msg);
        }else {
            JavaLog.print(tag,"Info",msg);
        }
    }

    public static void w(String tag,String msg){
        if(AppEnv.IsInAndroidPlatform){
            android.util.Log.w(tag,msg);
        }else {
            JavaLog.print(tag,"Warn",msg);
        }
    }

    public static void e(String tag,String msg){
        if(AppEnv.IsInAndroidPlatform){
            android.util.Log.e(tag,msg);
        }else {
            JavaLog.print(tag,"Error",msg);
        }
    }

    public static void v(String tag,String msg){
        if(AppEnv.IsInAndroidPlatform){
            android.util.Log.e(tag,msg);
        }else {
            JavaLog.print(tag,"Verbose",msg);
        }
    }

    private static class JavaLog{
        public static void print(String tag,String level,String msg){
            System.out.println(String.format("[%s] ==> %s: %s",level,tag,msg));
        }
    }
}
