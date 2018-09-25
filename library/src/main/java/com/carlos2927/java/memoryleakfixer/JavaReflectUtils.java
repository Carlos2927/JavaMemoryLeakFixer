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


    public static Field getField(String className,String fieldName){
        Map<String,Object> cache = JavaClassReflectCache.get(className);
        if(cache == null){
            cache = new HashMap<>();
            JavaClassReflectCache.put(className,cache);
        }
        Field field = (Field) cache.get(fieldName);
        if(field == null){
            try {
                Class cls = Class.forName(className);
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

    public static Field getField(Class cls,String fieldName){
        return getField(cls.getName(),fieldName);
    }

    public static Method getMethod(Class cls,String methodName,Class ... argTypes){
        return getMethod(cls.getName(),methodName,argTypes);
    }



    public static Method getMethod(String className,String methodName,Class ... argTypes){
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
                Class cls = Class.forName(className);
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

    public static Constructor getConstructor(Class cls,Class ... argTypes){
        return getConstructor(cls,argTypes);
    }
    public static Constructor getConstructor(String className,Class ... argTypes){
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
        String key = String.format("%s_init(%s)",className.substring(lastDotIndex != -1?lastDotIndex:0),argsFlag);
        Constructor constructor = (Constructor) cache.get(key);
        if(constructor == null){
            try {
                Class cls = Class.forName(className);
                constructor = cls.getConstructor(argTypes);
                constructor.setAccessible(true);
                cache.put(key,constructor);
            }catch (Exception e){
                e.printStackTrace();
                cache.put(key,Constructor_NO_FIND);
            }
        }else if(constructor == Constructor_NO_FIND){
            Log.e("JavaReflectUtils","No Such Constructor: "+className.substring(lastDotIndex != -1?lastDotIndex:0)+"("+argsFlag+") in class: "+className);
            return null;
        }
        return constructor;
    }

}
