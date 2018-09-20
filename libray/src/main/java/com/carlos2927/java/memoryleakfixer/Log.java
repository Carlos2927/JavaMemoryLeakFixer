package com.carlos2927.java.memoryleakfixer;

import java.lang.reflect.Method;

public class Log {
    //private static Method[] androidLogMethod = null;
    public static void d(String tag,String msg){
        if(AppEnv.IsInAndroidPlatform){
//             if(AppEnv.IsUseInJavaMemoryLeakFixSourceTest){
//                 if(androidLogMethod == null){
//                     androidLogMethod = new Method[5];
//                 }
//                 try {
//                     if(androidLogMethod[1] == null){
//                         androidLogMethod[1] = Class.forName("android.util.Log").getDeclaredMethod("d",String.class,String.class);
//                     }
//                     androidLogMethod[1].invoke(null,tag,msg);
//                 }catch (Exception e){
//                     e.printStackTrace();
//                 }
//             }else {
//                 android.util.Log.d(tag,msg);
//             }
            android.util.Log.d(tag,msg);
        }else {
            JavaLog.print(tag,"Debug",msg);
        }
    }
    public static void i(String tag,String msg){
        if(AppEnv.IsInAndroidPlatform){
//             if(AppEnv.IsUseInJavaMemoryLeakFixSourceTest){
//                 if(androidLogMethod == null){
//                     androidLogMethod = new Method[5];
//                 }
//                 try {
//                     if(androidLogMethod[2] == null){
//                         androidLogMethod[2] = Class.forName("android.util.Log").getDeclaredMethod("i",String.class,String.class);
//                     }
//                     androidLogMethod[2].invoke(null,tag,msg);
//                 }catch (Exception e){
//                     e.printStackTrace();
//                 }
//             }else {
//                 android.util.Log.i(tag,msg);
//             }
            android.util.Log.i(tag,msg);
        }else {
            JavaLog.print(tag,"Info",msg);
        }
    }

    public static void w(String tag,String msg){
        if(AppEnv.IsInAndroidPlatform){
            //在JavaMemoryLeakFixer的androidDemo中这里直接调用android.util.Log.(tag,msg)报错，为什么？
            // java.lang.NoSuchMethodError: No static method w(Ljava/lang/String;Ljava/lang/String;)V in class Landroid/util/Log; or its super classes (declaration of 'android.util.Log' appears in /system/framework/framework.jar:classes2.dex)
//             if(AppEnv.IsUseInJavaMemoryLeakFixSourceTest){
//                 if(androidLogMethod == null){
//                     androidLogMethod = new Method[5];
//                 }
//                 try {
//                     if(androidLogMethod[3] == null){
//                         androidLogMethod[3] = Class.forName("android.util.Log").getDeclaredMethod("w",String.class,String.class);
//                     }
//                     androidLogMethod[3].invoke(null,tag,msg);
//                 }catch (Exception e){
//                     e.printStackTrace();
//                 }
//             }else {
//                 android.util.Log.w(tag,msg);
//             }
            android.util.Log.w(tag,msg);
        }else {
            JavaLog.print(tag,"Warn",msg);
        }
    }

    public static void e(String tag,String msg){
        if(AppEnv.IsInAndroidPlatform){
//             if(AppEnv.IsUseInJavaMemoryLeakFixSourceTest){
//                 if(androidLogMethod == null){
//                     androidLogMethod = new Method[5];
//                 }
//                 try {
//                     if(androidLogMethod[4] == null){
//                         androidLogMethod[4] = Class.forName("android.util.Log").getDeclaredMethod("e",String.class,String.class);
//                     }
//                     androidLogMethod[4].invoke(null,tag,msg);
//                 }catch (Exception e){
//                     e.printStackTrace();
//                 }
//             }else {
//                 android.util.Log.e(tag,msg);
//             }
            android.util.Log.e(tag,msg);
        }else {
            JavaLog.print(tag,"Error",msg);
        }
    }

    public static void v(String tag,String msg){
        if(AppEnv.IsInAndroidPlatform){
//             if(AppEnv.IsUseInJavaMemoryLeakFixSourceTest){
//                 if(androidLogMethod == null){
//                     androidLogMethod = new Method[5];
//                 }
//                 try {
//                     if(androidLogMethod[0] == null){
//                         androidLogMethod[0] = Class.forName("android.util.Log").getDeclaredMethod("v",String.class,String.class);
//                     }
//                     androidLogMethod[0].invoke(null,tag,msg);
//                 }catch (Exception e){
//                     e.printStackTrace();
//                 }
//             }else {
//                 android.util.Log.v(tag,msg);
//             }
            android.util.Log.v(tag,msg);
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
