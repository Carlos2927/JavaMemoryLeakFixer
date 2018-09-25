package com.carlos2927.java.memoryleakfixer;

import android.app.Activity;
import android.app.Dialog;
import android.app.Fragment;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.Message;
import android.util.LruCache;
import android.view.View;
import android.widget.PopupWindow;

import java.lang.ref.WeakReference;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;

/**
 * 匿名内部类工具类，主要解决匿名内部类对象隐式引用外部类对象而引发的内存泄漏问题,需要注册匿名内部类代理类  
 * 已经实现的默认匿名内部类代理类有：                                                                
 * @see SimpleInnerClassProxyClassForRunnable Runnable接口的代理类                                 
 * @see SimpleInnerClassProxyClassForHandler Handler类的代理类                                    
 * @see SimpleInnerClassProxyClassForBroadcastReceiver BroadcastReceiver接口的代理类              
 */
public class InnerClassHelper {
    ////www.jianshu.com/p/9335c15c43cf

    private static final String TAG = "InnerClassHelper";

    private static final HashMap<String,Class> proxyClassMap = new HashMap<>();
    private static final LruCache<String,List<Field>> InnerClassHolderCache = new LruCache<String,List<Field>>(64){
        @Override
        protected int sizeOf(String key, List<Field> value) {
            return 1;
        }
    };
    private static final List<WeakReference<InnerClassTarget>> InnerClassTargetList = new ArrayList<>(64);
    private static boolean isRunning = false;
    private static final Object lock = new Object();

//    public static Object getExternalClass(Object target) throws NoSuchFieldException {
//        return getField(target, null, null);
//    }
//    private static Object getField(Object target, String name, Class classCache) throws NoSuchFieldException {
//        if (classCache == null) {
//            classCache = target.getClass();
//        }
//        if (name == null || name.isEmpty()) {
//            name = "this$0";
//        }
//        Field field = classCache.getDeclaredField(name);
//        field.setAccessible(true);
//        if (checkModifier(field.getModifiers())) {
//            try {
//                return field.get(target);
//            } catch (IllegalAccessException e) {
//                throw new RuntimeException(e);
//            }
//        }
//        return getField(target, name + "$", classCache);
//    }

    public static List<Field> getSyntheticFields(Class targetClass){
        Field[] fields = targetClass.getDeclaredFields();
        if(fields != null && fields.length>0){
            ArrayList<Field> fieldArrayList = new ArrayList<>();
            for(Field f:fields){
                if(JavaReflectUtils.checkModifierIfSynthetic(f.getModifiers())){
                    fieldArrayList.add(f);
                }
            }
            if(fieldArrayList.size()>0){
                return fieldArrayList;
            }
        }
        return null;
    }

    /**
     * 手动检查释放需要释放匿名内部类对象（可以在Activity.onDestroy()中调用）
     */
    static void tryToWatch(){
        synchronized (lock){
            lock.notifyAll();
        }
    }

    /**
     * 应用退出时调用(释放占用资源)
     */
    static void release(){
        synchronized (lock){
            isRunning = false;
            proxyClassMap.clear();
            InnerClassHolderCache.evictAll();
            lock.notifyAll();
        }
    }

    static void initLoopThread(){
        if(!isRunning){
            Thread thread = new Thread(){
                @Override
                public void run() {
                    int hashCode = hashCode();
                    Log.i(TAG,String.format("%s[@%x] prepare start...",getName(),hashCode));
                    try {
                        Thread.sleep(AppEnv.InnerClassHelperLoopCheckingThread_InitDelayDuration);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    isRunning = true;
                    Log.i(TAG,String.format("%s[@%x] running",getName(),hashCode));
                    List<WeakReference<InnerClassTarget>> toDelete = new ArrayList<>();
                    int count = 0;
                    int InnerClassHelperLoopCheckingThread_FindEmptyDuration = AppEnv.InnerClassHelperLoopCheckingThread_FindEmptyDuration;
                    for(;isRunning;){
                        synchronized (lock){
                            if(InnerClassTargetList.isEmpty()){
                                if(count%60==0){
                                    Log.i(TAG,String.format("%s[@%x] InnerClassTargetList is empty, LoopCount = %d , waiting...",getName(),hashCode,count));
                                }
                                try {
                                    lock.wait(InnerClassHelperLoopCheckingThread_FindEmptyDuration);
                                } catch (InterruptedException e) {
                                    e.printStackTrace();
                                }
                            }
                            //first,try to release all the instances of innerclass
                            try {
                                for(WeakReference<InnerClassTarget> innerClassTargetWeakReference:InnerClassTargetList){
                                    if(innerClassTargetWeakReference.get() == null){
                                        innerClassTargetWeakReference.clear();
                                        toDelete.add(innerClassTargetWeakReference);
                                    }else {
                                        InnerClassTarget innerClassTarget = innerClassTargetWeakReference.get();
                                        Object innerClassInstance = innerClassTarget.getInnerClassInstance();
                                        if(innerClassInstance != null){
                                            ImplicitReferenceChecker implicitReferenceChecker = innerClassTarget.getImplicitReferenceChecker();
                                            if(implicitReferenceChecker != null){
                                                List<Field> fields = innerClassTarget.getImplicitReferenceFields();
                                                if(fields != null && implicitReferenceChecker.checkImplicitReferenceDestroyed(fields,innerClassInstance)){
                                                    toDelete.add(innerClassTargetWeakReference);
                                                    innerClassTarget.clearInnerClassInstance();
                                                    innerClassTargetWeakReference.clear();
                                                }
                                            }
                                        }else {
                                            innerClassTarget.clearInnerClassInstance();
                                            toDelete.add(innerClassTargetWeakReference);
                                            innerClassTargetWeakReference.clear();
                                        }
                                    }
                                }
                            }catch (Exception e){
                                e.printStackTrace();
                            }

                            int size = toDelete.size();
                            if(size>0){
                                InnerClassTargetList.removeAll(toDelete);
                                toDelete.clear();
                                Log.d(TAG,String.format("%s[@%x] InnerClassTargetList size: %d,cleared: %d",getName(),hashCode,InnerClassTargetList.size(),size));
                            }
                            //then,try to release all the static field
                            Collection<Watchable> watchables =  JavaMemoryLeakFixer.ClassWatchers.values();
                            for(Watchable watchable:watchables){
                                try {
                                    if(watchable != null){
                                        watchable.watch();
                                    }
                                }catch (Exception e){
                                    e.printStackTrace();
                                }
                            }
                            try {
                                lock.wait(InnerClassHelperLoopCheckingThread_FindEmptyDuration*2);
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                            count++;
                            if(count == Integer.MAX_VALUE){
                                count = 0;
                            }
                        }
                    }
                    Log.i(TAG,String.format("%s[@%x] stopped",getName(),hashCode));
                }
            };
            thread.setDaemon(true);
            thread.setName("InnerClassHelperLoopCheckingThread");
//            thread.setPriority(Process.THREAD_PRIORITY_BACKGROUND);
            thread.setPriority(10);
            thread.start();

        }
    }



    public  static <T> T createProxyInnerClassInstance(T innerClassInstance){
        return createProxyInnerClassInstance(innerClassInstance,DefaultImplicitReferenceChecker);
    }

    public  static <T> T createProxyInnerClassInstance(T innerClassInstance,ImplicitReferenceChecker implicitReferenceChecker){
        return createProxyInnerClassInstance(innerClassInstance,false,implicitReferenceChecker);
    }

    public  static <T> T createProxyInnerClassInstance(T innerClassInstance,boolean isDelayCheck){
        return createProxyInnerClassInstance(innerClassInstance,isDelayCheck,DefaultImplicitReferenceChecker);
    }

    public  static <T> T createProxyInnerClassInstance(T innerClassInstance,boolean isDelayCheck,ImplicitReferenceChecker implicitReferenceChecker){
        return createProxyInnerClassInstance(innerClassInstance,null,isDelayCheck,implicitReferenceChecker);
    }

    /**
     * 清空匿名内部类默认的隐式引用属性(对于不好用匿名内部类对象代理类（通过实现InnerClassTarget接口的方式）的匿名内部类对象的java类如Thread，可以清空匿名Thread类对象的隐式引用，防止线程造成内存泄漏，
     * 注意清空之后不要在匿名内部类对象对象中再调用相关外部类方法与属性，防止导致空指针异常)
     * @param innerClassInstance 匿名内部类对象
     */
    public static void clearInnerClassInstanceDefaultImplicitReferences(Object innerClassInstance){
        clearInnerClassInstanceImplicitReferences(innerClassInstance,DefaultImplicitReferenceChecker);
    }

    /**
     * 清空匿名内部类所有的隐式引用属性(对于不好用匿名内部类对象代理类（通过实现InnerClassTarget接口的方式）的匿名内部类对象的java类如Thread，可以清空匿名Thread类对象的隐式引用，防止线程造成内存泄漏，
     * 注意清空之后不要在匿名内部类对象对象中再调用相关外部类方法与属性，防止导致空指针异常)
     * @param innerClassInstance 匿名内部类对象
     */
    public static void clearInnerClassInstanceAllImplicitReferences(Object innerClassInstance){
        clearInnerClassInstanceImplicitReferences(innerClassInstance,null);
    }

    /**
     * 清除匿名内部类的隐式引用属性(对于不好用匿名内部类对象代理类（通过实现InnerClassTarget接口的方式）的匿名内部类对象的java类如Thread，可以清空匿名Thread类对象的隐式引用，防止线程造成内存泄漏，    
     * 注意清空之后不要在匿名内部类对象对象中再调用相关外部类方法与属性，防止导致空指针异常)
     * @param innerClassInstance 匿名内部类对象
     * @param implicitReferenceChecker 隐式引用属性检测器
     */
    public static void clearInnerClassInstanceImplicitReferences(Object innerClassInstance,ImplicitReferenceChecker implicitReferenceChecker){
        if(innerClassInstance != null){
            Class targetClass = innerClassInstance.getClass();
            List<Field> syntheticFieldsFields = getSyntheticFields(targetClass);
            if(syntheticFieldsFields != null){
                for(Field field:syntheticFieldsFields){
                    try{
                        field.setAccessible(true);
                        if(implicitReferenceChecker == null || implicitReferenceChecker.isNeedFilter(field,innerClassInstance)){
                            field.set(innerClassInstance,null);
                        }
                    }catch (Exception e){
                        e.printStackTrace();
                    }
                }
            }
        }


    }

    /**
     * 创建匿名内部类对象的代理类对象
     * @param innerClassInstance 创建匿名内部类对象
     * @param proxyClass   匿名内部类对象的代理类 传空将使用默认注册的
     * @param isDelayCheck 设置是否需要延迟检测,如果设置成true,则必须在匿名内部类执行可能导致内存溢出的代码执行之前调用一次 InnerClassTarget.notifyNeedCheck()方法
     * @param implicitReferenceChecker 设置匿名内部类对象隐式引用属性检测器
     * @param <T> 匿名内部类与其代理类的共同父类或者父接口
     * @return 匿名内部类对象的代理类对象
     */
    public  static <T> T createProxyInnerClassInstance(T innerClassInstance,Class<? extends InnerClassTarget<T>> proxyClass,boolean isDelayCheck,ImplicitReferenceChecker implicitReferenceChecker){
        if(!isRunning){
            Log.e(TAG,"The Watch Thread Is Not Running! Please Make Sure You Called JavaMemoryLeakFixer.startWatchJavaMemory()!!!");
            return null;
        }
        Class targetClass = innerClassInstance.getClass();
        String key = targetClass.getName();
        List<Field> syntheticFieldsFields = InnerClassHolderCache.get(key);
        if(syntheticFieldsFields == null){
            syntheticFieldsFields = getSyntheticFields(targetClass);
            InnerClassHolderCache.put(key,syntheticFieldsFields);
        }
        if(syntheticFieldsFields == null){
            return innerClassInstance;
        }
        T proxyInnerClassInstance = getProxyInnerClassInstance(targetClass,proxyClass,innerClassInstance);
        if(proxyInnerClassInstance != null){
            if(!InnerClassTarget.class.isInstance(proxyInnerClassInstance)){
                return innerClassInstance;
            }
            final InnerClassTarget<T> innerClassTarget = (InnerClassTarget<T>) proxyInnerClassInstance;
            if(implicitReferenceChecker == null){
                implicitReferenceChecker = DefaultImplicitReferenceChecker;
            }
            List<Field> fields = new ArrayList<>();
            for(Field f:syntheticFieldsFields){
                if(implicitReferenceChecker.isNeedFilter(f,innerClassInstance)){
                    fields.add(f);
                }
            }
            innerClassTarget.setImplicitReferenceFields(fields);
            innerClassTarget.setImplicitReferenceChecker(implicitReferenceChecker);
            if(isDelayCheck){
                innerClassTarget.setDelayCheckTask(new Runnable() {
                    @Override
                    public void run() {
                        synchronized (lock){
                            InnerClassTargetList.add(new WeakReference<InnerClassTarget>(innerClassTarget));
                            lock.notifyAll();
                        }
                    }
                });
            }else {
                synchronized (lock){
                    InnerClassTargetList.add(new WeakReference<InnerClassTarget>(innerClassTarget));
                    lock.notifyAll();
                }
            }
        }
        return proxyInnerClassInstance != null? proxyInnerClassInstance :innerClassInstance;
    }


    /**
     * 注册匿名内部类代理类
     * @param proxyClass 匿名内部类代理类，必须实现InnerClassTarget接口
     * @param <T> 匿名内部类与其代理类的共同父类或者父接口
     */
    public static <T> void registerSupperClassOfInnerClassProxyClass(Class<? extends T> proxyClass){
        proxyClassMap.put(findCommonSuperClass(null,proxyClass).getName(),proxyClass);
    }



    /**
     * 清除匿名内部类代理类
     * @param proxyClass 匿名内部类代理类，必须实现InnerClassTarget接口
     * @param <T> 匿名内部类与其代理类的共同父类或者父接口
     */
    public static <T> void unregisterSupperClassOfInnerClassProxyClass(Class<? extends T> proxyClass){
        proxyClassMap.remove(findCommonSuperClass(null,proxyClass).getName());
    }

    private static Class findCommonSuperClass(Class innerClass,Class proxyClass){
        Class[] interfaces = innerClass!=null?innerClass.getInterfaces():null;
        Class innerClassSuperClass = innerClass!=null?innerClass.getSuperclass():null;
        Class[] proxyClassInterfaces = proxyClass != null?proxyClass.getInterfaces():null;
        Class proxyClassSuperClass = proxyClass != null?proxyClass.getSuperclass():null;
        if(innerClass != null){
            if(interfaces != null && interfaces.length>0){
                if(proxyClassInterfaces != null && proxyClassInterfaces.length>1){
                    for(Class cls:interfaces){
                        for(Class c:proxyClassInterfaces){
                            if(cls == c &&  cls != InnerClassTarget.class){
                                return cls;
                            }
                        }
                    }
                }else {
                    return interfaces[0];
                }
            }
            return innerClassSuperClass;
        }else {
            if(proxyClassInterfaces != null && proxyClassInterfaces.length>0){
                if(proxyClassInterfaces.length == 1){
                    return proxyClassSuperClass;
                }
                for(Class cls:proxyClassInterfaces){
                    if(cls != InnerClassTarget.class){
                        return cls;
                    }
                }
            }

        }
        return null;
    }

    public static <T> T getProxyInnerClassInstance(Class innerClass,Class<? extends InnerClassTarget<T>> proxyClass,T innerClassInstance){
        Class cls = findCommonSuperClass(innerClass,proxyClass);
        if(cls == null){
            return null;
        }
        if(proxyClass == null){
            proxyClass = proxyClassMap.get(cls.getName());
        }
        try {
            if(proxyClass != null){
                Constructor constructor = proxyClass.getDeclaredConstructor(cls);
                return (T)constructor.newInstance(innerClassInstance);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    public static boolean isActivityDestroyed(Activity activity){
        return activity == null || activity.isFinishing() || (AppEnv.AndroidSDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1?activity.isDestroyed():false);
    }

    public static Activity getActivityFromContext(Context mContext){
        Context context = mContext;
        while (context instanceof ContextWrapper) {
            if (context instanceof Activity) {
                return (Activity) context;
            }
            context = ((ContextWrapper) context).getBaseContext();
        }
        if(context instanceof Activity){
            return (Activity) context;
        }
        return null;
    }

    private static  ImplicitReferenceChecker DefaultNoAndroidPlatformImplicitReferenceChecker = null;

    public static void setDefaultNoAndroidPlatformImplicitReferenceChecker(ImplicitReferenceChecker noAndroidPlatformImplicitReferenceChecker){
        DefaultNoAndroidPlatformImplicitReferenceChecker = noAndroidPlatformImplicitReferenceChecker;
    }

    private static final ImplicitReferenceChecker DefaultImplicitReferenceChecker = new ImplicitReferenceChecker(){
        @Override
        public boolean isNeedFilter(Field field,Object innerClassInstance) {
            if(innerClassInstance != null && field != null){
                try {
                    field.setAccessible(true);
                    Object obj = field.get(innerClassInstance);
                    if(obj != null){
                        Class type = field.getType();
                        if(AppEnv.IsInAndroidPlatform){
                            if(View.class.isAssignableFrom(type) || Context.class.isAssignableFrom(type)
                                    || (AppEnv.AndroidSDK_INT >= Build.VERSION_CODES.HONEYCOMB && Fragment.class.isAssignableFrom(type)) || (AppEnv.HasAndroidSupportLibraryV4 && android.support.v4.app.Fragment.class.isAssignableFrom(type))
                                    || Dialog.class.isAssignableFrom(type) || PopupWindow.class.isAssignableFrom(type)){
                                return true;
                            }
                        }else {
                            if(DefaultNoAndroidPlatformImplicitReferenceChecker != null){
                                return DefaultNoAndroidPlatformImplicitReferenceChecker.isNeedFilter(field,innerClassInstance);
                            }
                        }

                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            return false;
        }

        @Override
        public boolean checkImplicitReferenceDestroyed(List<Field> fields,Object innerClassInstance) {
            for(Field f:fields){
                try {
                    Class type = f.getType();
                    if(AppEnv.IsInAndroidPlatform){
                        Context context = null;
                        if(View.class.isAssignableFrom(type)){
                            View view = (View) f.get(innerClassInstance);
                            if(view == null){
                                return true;
                            }
                            context = view.getContext();
                            if(context == null){
                                return true;
                            }
                            Activity activity = getActivityFromContext(context);
                            if(activity != null && isActivityDestroyed(activity)){
                                return true;
                            }
                        }else if(Context.class.isAssignableFrom(type)){
                            context = (Context) f.get(innerClassInstance);
                            if(context == null){
                                return true;
                            }
                            Activity activity = getActivityFromContext(context);
                            if(activity != null && isActivityDestroyed(activity)){
                                return true;
                            }
                        }else if(AppEnv.AndroidSDK_INT>= Build.VERSION_CODES.HONEYCOMB && Fragment.class.isAssignableFrom(type)){
                            Fragment fragment = (Fragment) f.get(innerClassInstance);
                            if(fragment == null || fragment.isRemoving() || fragment.isDetached()){
                                return true;
                            }
                            context = fragment.getActivity();
                            if(context == null){
                                return true;
                            }else{
                                Activity activity = (Activity) context;
                                if (isActivityDestroyed(activity)) {
                                    return true;
                                }
                                if (AppEnv.AndroidSDK_INT >= Build.VERSION_CODES.M) {
                                    context = fragment.getContext();
                                    if(context == null){
                                        return true;
                                    }
                                }
                            }
                        }else if(AppEnv.HasAndroidSupportLibraryV4 && android.support.v4.app.Fragment.class.isAssignableFrom(type)){
                            android.support.v4.app.Fragment fragment = (android.support.v4.app.Fragment) f.get(innerClassInstance);
                            if(fragment == null || fragment.isRemoving() || fragment.isDetached()){
                                return true;
                            }
                            context = fragment.getContext();
                            if(context == null){
                                return true;
                            }
                            Activity activity = fragment.getActivity();
                            if (isActivityDestroyed(activity)) {
                                return true;
                            }

                        }else if(Dialog.class.isAssignableFrom(type)){
                            Dialog dialog = (Dialog) f.get(innerClassInstance);
                            if(dialog==null || !dialog.isShowing()){
                                return true;
                            }
                            context = dialog.getContext();
                            if(context == null){
                                return true;
                            }
                            Activity activity = getActivityFromContext(context);
                            if(activity != null && isActivityDestroyed(activity)){
                                return true;
                            }
                            if(activity == null){
                                activity = dialog.getOwnerActivity();
                            }
                            if(activity != null && isActivityDestroyed(activity)){
                                return true;
                            }

                        }else if(PopupWindow.class.isAssignableFrom(type)){
                            PopupWindow popupWindow = (PopupWindow) f.get(innerClassInstance);
                            if(popupWindow == null || !popupWindow.isShowing()){
                                return true;
                            }
                            View v = popupWindow.getContentView();
                            if(v != null){
                                context = v.getContext();
                                if(context == null){
                                    return true;
                                }
                                Activity activity = getActivityFromContext(context);
                                if(activity != null && isActivityDestroyed(activity)){
                                    return true;
                                }
                            }
                        }
                    }else if(DefaultNoAndroidPlatformImplicitReferenceChecker != null){
                        if(DefaultNoAndroidPlatformImplicitReferenceChecker.checkImplicitReferenceDestroyed(fields,innerClassInstance)){
                            return true;
                        }
                    }

                }catch (Exception e){
                    e.printStackTrace();
                }

            }
            return false;
        }
    };

    /**
     * 匿名内部类隐式引用属性检测器
     */
    public static abstract class ImplicitReferenceChecker{
        /**
         * 判断匿名内部类对象的隐式属性是否需要防止内存泄漏
         * @param field   匿名内部类对象的隐式属性
         * @param innerClassInstance  匿名内部类对象
         * @return 判断是否需要过滤
         */
        public abstract boolean isNeedFilter(Field field,Object innerClassInstance);

        /**
         * 检测匿名内部类对象所引用的对象是否已经被销毁了
         * @param fields   匿名内部类对象的隐式属性
         * @param innerClassInstance 匿名内部类对象
         * @return 判断是否已经被销毁
         */
        public abstract boolean checkImplicitReferenceDestroyed(List<Field> fields,Object innerClassInstance);
    }

    /**
     * 匿名内部类代理类实现接口
     * @param <T> 匿名内部类对象的父类或者父接口
     */
    //https://github.com/BryanSharp/hibeaver java字节码修改神器
    public  interface InnerClassTarget<T>{
        /**
         * 获取匿名内部类对象
         * @return 匿名内部类对象
         */
        T getInnerClassInstance();

        /**
         * 清楚匿名内部类对象
         */
        void clearInnerClassInstance();

        /**
         * 设置匿名内部类隐式引用属性
         * @param fields 匿名内部类隐式引用属性
         */
        void setImplicitReferenceFields(List<Field> fields);

        /**
         * 获取匿名内部类隐式引用属性
         * @return 匿名内部类隐式引用属性
         */
        List<Field> getImplicitReferenceFields();

        /**
         * 设置匿名内部类隐式引用属性检测器
         * @param implicitReferenceChecker 匿名内部类隐式引用属性检测器
         */
        void setImplicitReferenceChecker(ImplicitReferenceChecker implicitReferenceChecker);

        /**
         * 获取匿名内部类隐式引用属性检测器
         * @return 匿名内部类隐式引用属性检测器
         */
        ImplicitReferenceChecker getImplicitReferenceChecker();

        /***
         * 设置延迟检测任务
         * @param delayTask 延迟检测任务
         */
        void setDelayCheckTask(Runnable delayTask);

        /**
         * 执行延迟检测任务(请保证只执行一次)
         */
        void notifyNeedCheck();
    }



    /**
     * Runnable的简单的匿名内部类代理类
     */
    public static class SimpleInnerClassProxyClassForRunnable implements Runnable,InnerClassTarget<Runnable>{
        Runnable innerClassInstance;
        List<Field> fields;
        ImplicitReferenceChecker implicitReferenceChecker;
        Runnable delayTask;
        boolean willRunOnceAtThread;
        public SimpleInnerClassProxyClassForRunnable(Runnable innerClassInstance){
            this.innerClassInstance = innerClassInstance;
        }
        @Override
        public Runnable getInnerClassInstance() {
            return innerClassInstance;
        }

        @Override
        public void clearInnerClassInstance() {
            innerClassInstance = null;
            fields = null;
            implicitReferenceChecker = null;
        }

        @Override
        public void setImplicitReferenceFields(List<Field> fields) {
            this.fields = fields;
        }

        @Override
        public List<Field> getImplicitReferenceFields() {
            return fields;
        }

        @Override
        public void setImplicitReferenceChecker(ImplicitReferenceChecker implicitReferenceChecker) {
            this.implicitReferenceChecker = implicitReferenceChecker;
        }

        public void willRunOnceAtThread(){
            willRunOnceAtThread = true;
        }

        @Override
        public ImplicitReferenceChecker getImplicitReferenceChecker() {
            return implicitReferenceChecker;
        }

        @Override
        public void setDelayCheckTask(Runnable delayTask) {
            this.delayTask = delayTask;
        }

        @Override
        public synchronized void notifyNeedCheck() {
            //保证延迟检测任务只执行一次
            if(delayTask != null){
                delayTask.run();
                delayTask = null;
            }
        }

        @Override
        public void run() {
            willRunOnceAtThread = false;
            if(innerClassInstance != null){
                innerClassInstance.run();
            }else {
                Log.w("InnerClassTarget","innerClassInstance已被清空");
            }
        }
    }



    /**
     * Handler的简单的匿名内部类代理类
     */
    public static class SimpleInnerClassProxyClassForHandler extends Handler implements InnerClassTarget<Handler>{
        List<Field> fields;
        ImplicitReferenceChecker implicitReferenceChecker;
        Runnable delayTask;
        Handler innerClassInstance;
        public SimpleInnerClassProxyClassForHandler(Handler innerClassInstance){
            super(innerClassInstance.getLooper());
            this.innerClassInstance = innerClassInstance;
        }
        @Override
        public Handler getInnerClassInstance() {
            return innerClassInstance;
        }

        @Override
        public void clearInnerClassInstance() {
            innerClassInstance = null;
            delayTask = null;
            implicitReferenceChecker = null;
        }

        @Override
        public void setImplicitReferenceFields(List<Field> fields) {
            this.fields = fields;
        }

        @Override
        public List<Field> getImplicitReferenceFields() {
            return fields;
        }

        @Override
        public void setImplicitReferenceChecker(ImplicitReferenceChecker implicitReferenceChecker) {
            this.implicitReferenceChecker = implicitReferenceChecker;
        }

        @Override
        public ImplicitReferenceChecker getImplicitReferenceChecker() {
            return implicitReferenceChecker;
        }

        @Override
        public void setDelayCheckTask(Runnable delayTask) {
            this.delayTask = delayTask;
        }

        @Override
        public final synchronized void notifyNeedCheck() {
            if(delayTask != null){
                delayTask.run();
                delayTask = null;
            }
        }

        @Override
        public void handleMessage(Message msg) {
            if(innerClassInstance != null){
                innerClassInstance.handleMessage(msg);
            }
        }

        @Override
        public boolean sendMessageAtTime(Message msg, long uptimeMillis) {
            return innerClassInstance != null ? innerClassInstance.sendMessageAtTime(msg,uptimeMillis) : true;
        }
    }

    /**
     * BroadcastReceiver的简单的匿名内部类代理类
     */
    public static class SimpleInnerClassProxyClassForBroadcastReceiver extends BroadcastReceiver implements InnerClassTarget<BroadcastReceiver>{
        BroadcastReceiver innerClassInstance;
        List<Field> fields;
        ImplicitReferenceChecker implicitReferenceChecker;
        Runnable delayTask;
        public SimpleInnerClassProxyClassForBroadcastReceiver(BroadcastReceiver innerClassInstance){
            this.innerClassInstance = innerClassInstance;
        }
        @Override
        public void onReceive(Context context, Intent intent) {
            if(innerClassInstance != null){
                innerClassInstance.onReceive(context,intent);
            }
        }

        @Override
        public BroadcastReceiver getInnerClassInstance() {
            return innerClassInstance;
        }

        @Override
        public void clearInnerClassInstance() {
            delayTask = null;
            innerClassInstance = null;
            implicitReferenceChecker = null;
            fields = null;
        }

        @Override
        public void setImplicitReferenceFields(List<Field> fields) {
            this.fields = fields;
        }

        @Override
        public List<Field> getImplicitReferenceFields() {
            return fields;
        }

        @Override
        public void setImplicitReferenceChecker(ImplicitReferenceChecker implicitReferenceChecker) {
            this.implicitReferenceChecker = implicitReferenceChecker;
        }

        @Override
        public ImplicitReferenceChecker getImplicitReferenceChecker() {
            return implicitReferenceChecker;
        }

        @Override
        public void setDelayCheckTask(Runnable delayTask) {
            this.delayTask = delayTask;
        }

        @Override
        public void notifyNeedCheck() {
            if(delayTask != null){
                delayTask.run();
                delayTask = null;
            }
        }
    }

    static {
        if(AppEnv.IsInAndroidPlatform){
            registerSupperClassOfInnerClassProxyClass(SimpleInnerClassProxyClassForBroadcastReceiver.class);
            registerSupperClassOfInnerClassProxyClass(SimpleInnerClassProxyClassForHandler.class);
        }
        registerSupperClassOfInnerClassProxyClass(SimpleInnerClassProxyClassForRunnable.class);
    }

}
