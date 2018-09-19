package com.carlos2927.java.memoryleakfixer;

import android.app.Activity;
import android.content.Context;
import android.graphics.drawable.Drawable;
import android.view.View;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * 第三方sdk静态属性释放清空帮助类，防止引发内存泄漏问题
 */
public class ThirdPartySDKStaticFieldReleaseHelper {
    private static final HashMap<String,List<Field>> staticFieldHolder = new HashMap<>();
    private static final HashMap<String,FieldFilter> staticFieldFilterHolder = new HashMap<>();

    public static interface FieldFilter {
        boolean onInitField(Field field);
        boolean onReleaseField(Field field);
    }

    private final static FieldFilter defaultFiledFilter = new FieldFilter() {
        @Override
        public boolean onInitField(Field field) {
            return AppEnv.IsInAndroidPlatform  && (Context.class.isAssignableFrom(field.getType()) || View.class.isAssignableFrom(field.getType()) || Drawable.class.isAssignableFrom(field.getType()));
        }

        @Override
        public boolean onReleaseField(Field field) {
            try {
                Object obj = field.get(null);
                if(AppEnv.IsInAndroidPlatform){
                    if(Activity.class.isInstance(obj) || View.class.isInstance(obj)){
                        return true;
                    }
                    if(Drawable.class.isInstance(obj)){
                        //默认Drawable没有清空 只是去除了activity引用
                        Drawable drawable = (Drawable) obj;
                        drawable.setCallback(null);
                    }
                }
            } catch (IllegalAccessException e) {
                e.printStackTrace();
            }
            return false;
        }
    };

    /**
     * 在Application.onCreate()中调用
     * @param className
     * @param filedFilter
     */
    public static void addWatchTargetClass(String className,FieldFilter filedFilter){
        if(filedFilter == null){
            filedFilter = defaultFiledFilter;
        }
        try {
            Class cls = Class.forName(className);
            Field[] fields = cls.getDeclaredFields();
            List<Field> fieldList = null;
            for(Field f:fields){
                if(Modifier.isStatic(f.getModifiers())){
                    if(filedFilter.onInitField(f)){
                        if(fieldList == null){
                            fieldList = new ArrayList<>();
                        }
                        f.setAccessible(true);
                        fieldList.add(f);
                    }
                }
            }
            if(fieldList != null){
                staticFieldHolder.put(className,fieldList);
                staticFieldFilterHolder.put(className,filedFilter);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 在Application.onCreate()中调用
     */
    public static void watchClassStaticFields(Class cls){
        addWatchTargetClass(cls.getName(),null);
    }

    /**
     * 在Application.onCreate()中调用
     * @param cls
     * @param fieldFilter
     */
    public static void watchClassStaticFields(Class cls,FieldFilter fieldFilter){
        addWatchTargetClass(cls.getName(),fieldFilter);
    }

    /**
     * 在合适的地方(如Activity.onDestroy()中)释放静态属性引用的资源
     * @param cls
     */
    public static void releaseWatchedClassStaticFields(Class cls){
        releaseWatchedClassStaticFields(cls.getName());
    }

    /**
     * 在合适的地方(如Activity.onDestroy()中)释放静态属性引用的资源
     * @param clsName
     */
    public static void releaseWatchedClassStaticFields(String clsName){
        List<Field> fieldList = staticFieldHolder.get(clsName);
        if(fieldList != null){
            FieldFilter fieldFilter = staticFieldFilterHolder.get(clsName);
            for(Field f:fieldList){
                if(fieldFilter.onReleaseField(f)){
                    try {
                        //静态属性清空
                        f.set(null,null);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        }
    }

    public static List<Field> getWatchedClassStaticFields(String clsName){
        List<Field> fieldList = staticFieldHolder.get(clsName);
        if(fieldList != null){
            return new ArrayList<>(fieldList);
        }
        return null;
    }

    static {
        AppEnv.setInnerClassHelperLoopCheckingThreadFindEmptyListSleepDuration(AppEnv.InnerClassHelperLoopCheckingThread_FindEmptyDuration);
    }

}
