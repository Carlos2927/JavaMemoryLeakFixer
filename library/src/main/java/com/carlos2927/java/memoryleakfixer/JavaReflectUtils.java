package com.carlos2927.java.memoryleakfixer;

import android.util.LruCache;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

public class JavaReflectUtils {

    private static Field Field_NO_FIND;
    private static Method Method_NO_FIND;
    private static Constructor Constructor_NO_FIND;
    private static final int SYNTHETIC = 0x00001000;
    private static final int FINAL = 0x00000010;
    private static final int SYNTHETIC_AND_FINAL = SYNTHETIC | FINAL;

    static {
        try {
            Field_NO_FIND = Byte.class.getField("TYPE");
            Method_NO_FIND = Byte.class.getMethod("hashCode");
            Constructor_NO_FIND = Object.class.getConstructor();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static final LruCache<String,Map<String,Object>> JavaClassReflectCache = new LruCache<String,Map<String,Object>>(64){
        @Override
        protected int sizeOf(String key, Map<String, Object> value) {
            return 1;
        }
    };

    /**
     * 通过属性修饰符检测是否为编译器为匿名内部类添加的内部属性
     * @param mod 属性修饰符
     * @return 是否为匿名内部类隐式引用
     */
    public static boolean checkModifierIfSynthetic(int mod) { return (mod & SYNTHETIC_AND_FINAL) == SYNTHETIC_AND_FINAL; }

    public static Field getFieldWithoutCache(String className,String fieldName){
        try {
            return getFieldWithoutCache(Class.forName(className),fieldName);
        }catch (Exception e){
            e.printStackTrace();
        }
        return null;
    }

    public static Field getFieldWithoutCache(Class cls,String fieldName){
        try {
            Field field = cls.getDeclaredField(fieldName);
            field.setAccessible(true);
            return field;
        }catch (Exception e){
            e.printStackTrace();
        }
        return null;
    }

    public static Method getMethodWithoutCache(String className,String methodName,Class ... argTypes){
        try {
            return getMethodWithoutCache(Class.forName(className),methodName,argTypes);
        }catch (Exception e){
            e.printStackTrace();
        }
        return null;
    }

    public static Method getMethodWithoutCache(Class cls,String methodName,Class ... argTypes){
        try {
            Method method = cls.getDeclaredMethod(methodName,argTypes);
            method.setAccessible(true);
            return method;
        }catch (Exception e){
            e.printStackTrace();
        }
        return null;
    }

    public static Constructor getConstructorWithoutCache(String className,Class ... argTypes){
        try {
            return getConstructorWithoutCache(Class.forName(className),argTypes);
        }catch (Exception e){
            e.printStackTrace();
        }
        return null;
    }

    public static Constructor getConstructorWithoutCache(Class cls,Class ... argTypes){
        try {
            Constructor constructor = cls.getDeclaredConstructor(argTypes);
            constructor.setAccessible(true);
            return constructor;
        }catch (Exception e){
            e.printStackTrace();
        }
        return null;
    }

    public static Field getField(String className,String fieldName){
        try {
            return getField(Class.forName(className),fieldName);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    public static Field getField(Class cls,String fieldName){
        String className = cls.getName();
        Map<String,Object> cache = JavaClassReflectCache.get(className);
        if(cache == null){
            cache = new HashMap<>();
            JavaClassReflectCache.put(className,cache);
        }
        Field field = (Field) cache.get(fieldName);
        if(field == null){
            try {
                field = cls.getDeclaredField(fieldName);
                field.setAccessible(true);
                cache.put(fieldName,field);
            }catch (Exception e){
                e.printStackTrace();
                cache.put(fieldName,Field_NO_FIND);
            }
        }else if(field == Field_NO_FIND){
            Log.e("JavaReflectUtils","No Such Field: "+fieldName+" in class: "+className);
            return null;
        }
        return field;
    }

    public static Method getMethod(Class cls,String methodName,Class ... argTypes){
        String className = cls.getName();
        Map<String,Object> cache = JavaClassReflectCache.get(className);
        if(cache == null){
            cache = new HashMap<>();
            JavaClassReflectCache.put(className,cache);
        }
        String argsFlag = "void";
        if(argTypes != null && argTypes.length>0){
            StringBuffer stringBuffer = new StringBuffer();
            for(Class argType:argTypes){
                stringBuffer.append(argType.getName());
                stringBuffer.append(",");
            }
            argsFlag = stringBuffer.toString();
        }
        String key = String.format("%s(%s)",methodName,argsFlag);
        Method method = (Method) cache.get(key);
        if(method == null){
            try {
                method = cls.getDeclaredMethod(methodName,argTypes);
                method.setAccessible(true);
                cache.put(key,method);
            }catch (Exception e){
                e.printStackTrace();
                cache.put(key,Method_NO_FIND);
            }
        }else if(method == Method_NO_FIND){
            Log.e("JavaReflectUtils","No Such Method: "+methodName+"("+argsFlag+") in class: "+className);
            return null;
        }
        return method;
    }



    public static Method getMethod(String className,String methodName,Class ... argTypes){
        try {
            return getMethod(Class.forName(className),methodName,argTypes);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    public static Constructor getConstructor(Class cls,Class ... argTypes){
        String className = cls.getName();
        Map<String,Object> cache = JavaClassReflectCache.get(className);
        if(cache == null){
            cache = new HashMap<>();
            JavaClassReflectCache.put(className,cache);
        }
        String argsFlag = "void";
        if(argTypes != null && argTypes.length>0){
            StringBuffer stringBuffer = new StringBuffer();
            for(Class argType:argTypes){
                stringBuffer.append(argType.getName());
                stringBuffer.append(",");
            }
            argsFlag = stringBuffer.toString();
        }
        int lastDotIndex = className.lastIndexOf(".");
        String substring = className.substring(lastDotIndex != -1 ? lastDotIndex : 0);
        String key = String.format("%s_init(%s)",substring,argsFlag);
        Constructor constructor = (Constructor) cache.get(key);
        if(constructor == null){
            try {
                constructor = cls.getConstructor(argTypes);
                constructor.setAccessible(true);
                cache.put(key,constructor);
            }catch (Exception e){
                e.printStackTrace();
                cache.put(key,Constructor_NO_FIND);
            }
        }else if(constructor == Constructor_NO_FIND){

            Log.e("JavaReflectUtils","No Such Constructor: "+ substring +"("+argsFlag+") in class: "+className);
            return null;
        }
        return constructor;
    }
    public static Constructor getConstructor(String className,Class ... argTypes){
        try {
            return getConstructor(Class.forName(className),argTypes);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

}
