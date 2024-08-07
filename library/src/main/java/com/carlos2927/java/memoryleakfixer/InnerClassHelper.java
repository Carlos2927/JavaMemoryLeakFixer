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

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.sql.SQLException;
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
//                return field._getLifeCycleObject(target);
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
        synchronized(lock){
            lock.notifyAll();
        }
    }

    /**
     * 应用退出时调用(释放占用资源)
     */
    static void release(){
        synchronized(lock){
            isRunning = false;
            proxyClassMap.clear();
            InnerClassHolderCache.evictAll();
            lock.notifyAll();
        }
    }

    static void initLoopThread(){
        if(!isRunning){
            Thread thread = new Thread(){

                private void clearInnerClassInstanceImplicitReferencesWhenClear(InnerClassTarget innerClassTarget,Object innerClassInstance){
                    if(innerClassInstance != null){
                        List<Field> fields = innerClassTarget.getImplicitReferenceFields();
                        if(fields != null){
                            for(Field f:fields){
                                try {
                                    f.set(innerClassInstance,null);
                                }catch (Exception e){
                                    e.printStackTrace();
                                }
                            }
                        }else {
                            clearInnerClassInstanceImplicitReferences(innerClassInstance,innerClassTarget.getImplicitReferenceChecker());
                        }
                    }
                }

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
                        WeakReference<InnerClassTarget>[] InnerClassTargetArray = null;
                        synchronized(lock){
                            if(InnerClassTargetList.isEmpty()){
                                if(count%60==0){
                                    Log.i(TAG,String.format("%s[@%x] InnerClassTargetList is empty, LoopCount = %d , waiting...",getName(),hashCode,count));
                                }
                                try {
                                    //sleep ...
                                    lock.wait(InnerClassHelperLoopCheckingThread_FindEmptyDuration);
                                } catch (InterruptedException e) {
                                    e.printStackTrace();
                                }
                            }
                            if(!isRunning){
                                break;
                            }
                            InnerClassTargetArray = InnerClassTargetList.toArray(new WeakReference[]{});
                        }
                        if(InnerClassTargetArray != null){
                            //first,try to release all the instances of innerclass
                            try {
                                for(WeakReference<InnerClassTarget> innerClassTargetWeakReference:InnerClassTargetArray){
                                    if(innerClassTargetWeakReference.get() == null){
                                        innerClassTargetWeakReference.clear();
                                        toDelete.add(innerClassTargetWeakReference);
                                    }else {
                                        InnerClassTarget innerClassTarget = innerClassTargetWeakReference.get();
                                        Object innerClassInstance = innerClassTarget.getInnerClassInstance();
                                        if(innerClassInstance != null){
                                            ImplicitReferenceChecker implicitReferenceChecker = innerClassTarget.getImplicitReferenceChecker();
                                            if(implicitReferenceChecker != null){
                                                //first,check the watched lifecycle object
                                                if(LifeCycleObjectDirectGetter.class.isInstance(innerClassTarget) && implicitReferenceChecker.checkLifeCycleObjectDestroyed((LifeCycleObjectDirectGetter) innerClassTarget)){
                                                    toDelete.add(innerClassTargetWeakReference);
                                                    synchronized (innerClassTarget){
                                                        if(innerClassTarget.isNeedClearInnerClassInstanceImplicitReferences()){
                                                            clearInnerClassInstanceImplicitReferencesWhenClear(innerClassTarget,innerClassInstance);
                                                        }
                                                        innerClassTarget.clearInnerClassInstance();
                                                    }
                                                    innerClassTargetWeakReference.clear();
                                                    continue;
                                                }
                                                //then,check all the implicit references
                                                List<Field> fields = innerClassTarget.getImplicitReferenceFields();
                                                if(fields != null && implicitReferenceChecker.checkImplicitReferenceDestroyed(fields,innerClassInstance)){
                                                    toDelete.add(innerClassTargetWeakReference);
                                                    synchronized (innerClassTarget){
                                                        if(innerClassTarget.isNeedClearInnerClassInstanceImplicitReferences()){
                                                            clearInnerClassInstanceImplicitReferencesWhenClear(innerClassTarget,innerClassInstance);
                                                        }
                                                        innerClassTarget.clearInnerClassInstance();
                                                    }
                                                    innerClassTargetWeakReference.clear();
                                                }
                                            }
                                        }else {
                                            synchronized (innerClassTarget){
                                                innerClassTarget.clearInnerClassInstance();
                                            }
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
                                synchronized (lock){
                                    InnerClassTargetList.removeAll(toDelete);
                                }
                                toDelete.clear();
                                Log.d(TAG,String.format("%s[@%x] InnerClassTargetList size: %d,cleared: %d",getName(),hashCode,InnerClassTargetList.size(),size));
                            }
                            if(isRunning){
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
                            }else {
                                break;
                            }
                        }
                        synchronized (lock){
                            try {
                                //sleep ...
                                lock.wait(InnerClassHelperLoopCheckingThread_FindEmptyDuration*2);
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                        }
                        count++;
                        if(count == Integer.MAX_VALUE){
                            count = 0;
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

    /**
     * 创建匿名内部类对象的代理类对象,这个方法适合innerClassInstance是在其他匿名内部类中直接创建时调用,直接传入需要监控的lifeCycleObject
     * @param isNeedClearInnerClassInstanceImplicitReferences 判断当匿名内部类对象在其代理类中清空时是否要清空匿名内部类对象的隐式引用,当匿名内部对象自身存在耗时操作时建议清空其隐式引用，以防出现内存泄漏。
     *                                                           同时需要注意的是当isNeedClearInnerClassInstanceImplicitReferences设置为true时，你必须在匿名内部类对象的方法调用中进行异常处理，防止耗时操作执行后引用外部属性或方法抛出空指针异常引发
     *                                                         应用崩溃
     * @param innerClassInstance 创建匿名内部类对象
     * @param <T> 匿名内部类与其代理类的共同父类或者父接口
     * @return 匿名内部类对象的代理类对象
     */
    public static <T> T createProxyInnerClassInstance(boolean isNeedClearInnerClassInstanceImplicitReferences,T innerClassInstance){
        return createProxyInnerClassInstance(null,false,isNeedClearInnerClassInstanceImplicitReferences,innerClassInstance);
    }

    public  static <T> T createProxyInnerClassInstance(T innerClassInstance,boolean isDelayCheck,ImplicitReferenceChecker implicitReferenceChecker){
        return createProxyInnerClassInstance(null,false,innerClassInstance,null,isDelayCheck,implicitReferenceChecker);
    }

    /**
     * 创建匿名内部类对象的代理类对象,这个方法适合innerClassInstance是在其他匿名内部类中直接创建时调用,直接传入需要监控的lifeCycleObject
     * @param lifeCycleObject 要监控的有生命周期的对象
     * @param innerClassInstance 创建匿名内部类对象
     * @param <T> 匿名内部类与其代理类的共同父类或者父接口
     * @return 匿名内部类对象的代理类对象
     */
    public static <T> T createProxyInnerClassInstance(Object lifeCycleObject,T innerClassInstance){
        return createProxyInnerClassInstance(lifeCycleObject,false,innerClassInstance);
    }

    /**
     * 创建匿名内部类对象的代理类对象,这个方法适合innerClassInstance是在其他匿名内部类中直接创建时调用,直接传入需要监控的lifeCycleObject
     * @param isNeedClearInnerClassInstanceImplicitReferences 判断当匿名内部类对象在其代理类中清空时是否要清空匿名内部类对象的隐式引用,当匿名内部对象自身存在耗时操作时建议清空其隐式引用，以防出现内存泄漏。
     *                                                           同时需要注意的是当isNeedClearInnerClassInstanceImplicitReferences设置为true时，你必须在匿名内部类对象的方法调用中进行异常处理，防止耗时操作执行后引用外部属性或方法抛出空指针异常引发
     *                                                         应用崩溃
     * @param lifeCycleObject 要监控的有生命周期的对象
     * @param innerClassInstance 创建匿名内部类对象
     * @param <T> 匿名内部类与其代理类的共同父类或者父接口
     * @return 匿名内部类对象的代理类对象
     */
    public static <T> T createProxyInnerClassInstance(boolean isNeedClearInnerClassInstanceImplicitReferences,Object lifeCycleObject,T innerClassInstance){
        return createProxyInnerClassInstance(lifeCycleObject,false,isNeedClearInnerClassInstanceImplicitReferences,innerClassInstance);
    }



    /**
     * 创建匿名内部类对象的代理类对象,这个方法适合innerClassInstance是在其他匿名内部类中直接创建时调用,直接传入需要监控的lifeCycleObject,当innerClassInstance不是在lifeCycleObject的生命周期方法中创建而是直接在
     * 变量声明中创建时，isDelayCheck必须设置为true,并在合适的声明周期方法中调用名内部类对象的代理类对象的notifyNeedCheck()方法
     * @param lifeCycleObject 要监控的有生命周期的对象
     * @param innerClassInstance 创建匿名内部类对象
     * @param isNeedClearInnerClassInstanceImplicitReferences 判断当匿名内部类对象在其代理类中清空时是否要清空匿名内部类对象的隐式引用,当匿名内部对象自身存在耗时操作时建议清空其隐式引用，以防出现内存泄漏。
     *                                                           同时需要注意的是当isNeedClearInnerClassInstanceImplicitReferences设置为true时，你必须在匿名内部类对象的方法调用中进行异常处理，防止耗时操作执行后引用外部属性或方法抛出空指针异常引发
     *                                                         应用崩溃
     * @param isDelayCheck 设置是否需要延迟检测,如果设置成true,则必须在匿名内部类执行可能导致内存溢出的代码执行之前调用一次 InnerClassTarget.notifyNeedCheck()方法
     * @param <T> 匿名内部类与其代理类的共同父类或者父接口
     * @return 匿名内部类对象的代理类对象
     */
    public static <T> T createProxyInnerClassInstance(Object lifeCycleObject,boolean isDelayCheck,boolean isNeedClearInnerClassInstanceImplicitReferences,T innerClassInstance){
        return createProxyInnerClassInstance(lifeCycleObject,isNeedClearInnerClassInstanceImplicitReferences,innerClassInstance,null,isDelayCheck,DefaultImplicitReferenceChecker);
    }

    /**
     * 创建匿名内部类对象的代理类对象,这个方法适合innerClassInstance是在其他匿名内部类中直接创建时调用,直接传入需要监控的lifeCycleObject,当innerClassInstance不是在lifeCycleObject的生命周期方法中创建而是直接在
     * 变量声明中创建时，isDelayCheck必须设置为true,并在合适的声明周期方法中调用名内部类对象的代理类对象的notifyNeedCheck()方法
     * @param lifeCycleObject 要监控的有生命周期的对象
     * @param innerClassInstance 创建匿名内部类对象
     * @param isDelayCheck 设置是否需要延迟检测,如果设置成true,则必须在匿名内部类执行可能导致内存溢出的代码执行之前调用一次 InnerClassTarget.notifyNeedCheck()方法
     * @param <T> 匿名内部类与其代理类的共同父类或者父接口
     * @return 匿名内部类对象的代理类对象
     */
    public static <T> T createProxyInnerClassInstance(Object lifeCycleObject,boolean isDelayCheck,T innerClassInstance){
        return createProxyInnerClassInstance( lifeCycleObject, isDelayCheck, false, innerClassInstance);
    }


    /**
     * 对非匿名内部类对象进行内存监听调用这个方法
     * @param lifeCycleObject
     * @param innerClassInstance
     * @param innerClassInstanceClass
     * @param <T>
     * @return
     */
    public static <T> T createProxyNoInnerClassInstance(Object lifeCycleObject,T innerClassInstance,Class<T> innerClassInstanceClass){
        return createProxyNoInnerClassInstance(lifeCycleObject,innerClassInstance,innerClassInstanceClass,false,null);
    }

    /**
     * 对非匿名内部类对象进行内存监听调用这个方法
     * @param lifeCycleObject
     * @param innerClassInstance
     * @param innerClassInstanceClass
     * @param isDelayCheck
     * @param lifeCycleObjectChecker
     * @param <T>
     * @return
     */
    public static <T> T createProxyNoInnerClassInstance(Object lifeCycleObject,T innerClassInstance,Class<T> innerClassInstanceClass,boolean isDelayCheck,ImplicitReferenceChecker lifeCycleObjectChecker){
        Class<? extends InnerClassTarget<T>> proxyClass = proxyClassMap.get(innerClassInstanceClass.getName());
        if(proxyClass!= null && lifeCycleObject != null){
            try {
                T   proxyInnerClassInstance = getProxyInnerClassInstance(innerClassInstanceClass,proxyClass,innerClassInstance);
                if(proxyInnerClassInstance != null){
                    if(!InnerClassTarget.class.isInstance(proxyInnerClassInstance)){
                        return innerClassInstance;
                    }

                    final InnerClassTarget<T> innerClassTarget = (InnerClassTarget<T>) proxyInnerClassInstance;
                    innerClassTarget.setIsNeedClearInnerClassInstanceImplicitReferences(false);
                    if(lifeCycleObjectChecker == null){
                        lifeCycleObjectChecker = DefaultImplicitReferenceChecker;
                    }
                    innerClassTarget.setImplicitReferenceChecker(lifeCycleObjectChecker);
                    if(LifeCycleObjectDirectGetter.class.isInstance(proxyInnerClassInstance)){
                        LifeCycleObjectDirectGetter lifeCycleObjectDirectGetter = (LifeCycleObjectDirectGetter) proxyInnerClassInstance;
                        lifeCycleObjectDirectGetter._setLifeCycleObject(lifeCycleObject);
                    }else {
                        return innerClassInstance;
                    }
                    if(isDelayCheck){
                        innerClassTarget.setDelayCheckTask(new Runnable() {
                            @Override
                            public void run() {
                                synchronized(lock){
                                    InnerClassTargetList.add(new WeakReference<InnerClassTarget>(innerClassTarget));
                                    lock.notifyAll();
                                }
                            }
                        });
                    }else {
                        synchronized(lock){
                            InnerClassTargetList.add(new WeakReference<InnerClassTarget>(innerClassTarget));
                            lock.notifyAll();
                        }
                    }
                    return proxyInnerClassInstance;
                }
            } catch (Exception e) {

            }
        }
        return innerClassInstance;
    }

    /**
     * 创建匿名内部类对象的代理类对象
     * @param lifeCycleObject 要监控的有生命周期的对象
     * @param isNeedClearInnerClassInstanceImplicitReferences 判断当匿名内部类对象在其代理类中清空时是否要清空匿名内部类对象的隐式引用,当匿名内部对象自身存在耗时操作时建议清空其隐式引用，以防出现内存泄漏。
     *                                                          同时需要注意的是当isNeedClearInnerClassInstanceImplicitReferences设置为true时，你必须在匿名内部类对象的方法调用中进行异常处理，防止耗时操作执行后引用外部属性或方法抛出空指针异常引发
     *                                                          应用崩溃
     * @param innerClassInstance 创建匿名内部类对象
     * @param proxyClass   匿名内部类对象的代理类 传空将使用默认注册的
     * @param isDelayCheck 设置是否需要延迟检测,如果设置成true,则必须在匿名内部类执行可能导致内存溢出的代码执行之前调用一次 InnerClassTarget.notifyNeedCheck()方法
     * @param implicitReferenceChecker 设置匿名内部类对象隐式引用属性检测器
     * @param <T> 匿名内部类与其代理类的共同父类或者父接口
     * @return 匿名内部类对象的代理类对象
     */
    public  static <T> T createProxyInnerClassInstance(Object lifeCycleObject,boolean isNeedClearInnerClassInstanceImplicitReferences,T innerClassInstance,Class<? extends InnerClassTarget<T>> proxyClass,boolean isDelayCheck,ImplicitReferenceChecker implicitReferenceChecker){
        if(!isRunning){
            Log.e(TAG,"The Watch Thread Is Not Running! Please Make Sure You Called JavaMemoryLeakFixer.startWatchJavaMemory()!!!");
//            return null;
        }
        Class targetClass = innerClassInstance.getClass();
        String key = targetClass.getName();
        List<Field> syntheticFieldsFields = InnerClassHolderCache.get(key);
        if(syntheticFieldsFields == null){
            syntheticFieldsFields = getSyntheticFields(targetClass);
            if(syntheticFieldsFields != null){
                InnerClassHolderCache.put(key,syntheticFieldsFields);
            }
        }
        if(syntheticFieldsFields == null){
            return innerClassInstance;
//            if(lifeCycleObject == null){
//                return innerClassInstance;
//            }
//            // 当innerClassInstance不是匿名内部类对象时
//            Class newTargetClass = null;
//
//            Class supperClass = targetClass;
//            Class newProxyClass = null;
//            while(supperClass != null && supperClass != Object.class){
//                // 如果T为innerClassInstance继承的类 则查出这个类与其代理实现类
//                if(proxyClassMap.get(supperClass.getName())!= null){
//                    newProxyClass = proxyClassMap.get(supperClass.getName());
//                    try {
//                        if(newProxyClass != null){
//                            Constructor constructor = newProxyClass.getDeclaredConstructor(supperClass);
//                            T a =  (T)constructor.newInstance(innerClassInstance);
//                            newTargetClass = supperClass;
//                            break;
//                        }
//                    } catch (Exception e) {
//
//                    }
//
//                }
//                if(newTargetClass == null){
//
//                    // 如果T为innerClassInstance实现的接口 则查出这个接口与其代理实现类
//                    Class[] interfaces = supperClass.getInterfaces();
//                    if(interfaces != null){
//                        for(Class cls :interfaces){
//                            if(proxyClassMap.get(cls.getName())!= null){
//                                newProxyClass = proxyClassMap.get(cls.getName());
//                                try {
//                                    if(newProxyClass != null){
//                                        Constructor constructor = newProxyClass.getDeclaredConstructor(cls);
//                                        T a =  (T)constructor.newInstance(innerClassInstance);
//                                        newTargetClass = cls;
//                                        break;
//                                    }
//                                } catch (Exception e) {
//
//                                }
//                            }
//                        }
//                    }
//
//                }
//                if(newTargetClass != null){
//                    break;
//                }
//                supperClass = supperClass.getSuperclass();
//            }
//            if(newTargetClass == null){
//                return innerClassInstance;
//            }
//            targetClass = newTargetClass;
//            proxyClass =  proxyClassMap.get(newTargetClass.getName());
        }
        T proxyInnerClassInstance = getProxyInnerClassInstance(targetClass,proxyClass,innerClassInstance);
        if(proxyInnerClassInstance != null){
            if(!InnerClassTarget.class.isInstance(proxyInnerClassInstance)){
                return innerClassInstance;
            }

            final InnerClassTarget<T> innerClassTarget = (InnerClassTarget<T>) proxyInnerClassInstance;
            innerClassTarget.setIsNeedClearInnerClassInstanceImplicitReferences(isNeedClearInnerClassInstanceImplicitReferences);
            if(implicitReferenceChecker == null){
                implicitReferenceChecker = DefaultImplicitReferenceChecker;
            }
            innerClassTarget.setImplicitReferenceChecker(implicitReferenceChecker);
            if(LifeCycleObjectDirectGetter.class.isInstance(proxyInnerClassInstance) && lifeCycleObject!=null){
                LifeCycleObjectDirectGetter lifeCycleObjectDirectGetter = (LifeCycleObjectDirectGetter) proxyInnerClassInstance;
                lifeCycleObjectDirectGetter._setLifeCycleObject(lifeCycleObject);
            }else {
                List<Field> fields = new ArrayList<>();
                for(Field f:syntheticFieldsFields){
                    if(implicitReferenceChecker.isNeedFilter(f,innerClassInstance)){
                        fields.add(f);
                    }
                }
                innerClassTarget.setImplicitReferenceFields(fields);
            }
            if(isDelayCheck){
                innerClassTarget.setDelayCheckTask(new Runnable() {
                    @Override
                    public void run() {
                        synchronized(lock){
                            InnerClassTargetList.add(new WeakReference<InnerClassTarget>(innerClassTarget));
                            lock.notifyAll();
                        }
                    }
                });
            }else {
                synchronized(lock){
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
                    if(field.getType().isPrimitive()){
                        continue;
                    }
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
        if(interfaces != null && interfaces.length>0){
            if(proxyClassInterfaces != null && proxyClassInterfaces.length>1){
                for(Class cls:interfaces){
                    for(Class c:proxyClassInterfaces){
                        if(cls == c &&  cls != InnerClassTarget.class && cls != LifeCycleObjectDirectGetter.class){
                            return cls;
                        }
                    }
                }
            }else {
                return interfaces[0];
            }
            return innerClassSuperClass;
        }else {
            if(proxyClassInterfaces != null && proxyClassInterfaces.length>0){
                Class superClass = proxyClassSuperClass.getSuperclass();
                //if the common super class is interface
                if(proxyClassSuperClass == Object.class && superClass==null){
                    for(Class cls:proxyClassInterfaces){
                        // the proxy class of a interface,must first implement the interface,then implement others,
                        // the proxy class must implements InnerClassTarget<OriginInterface>,the implements of LifeCycleObjectDirectGetter is optional
                        if(cls != InnerClassTarget.class && cls != LifeCycleObjectDirectGetter.class){
                            Log.w(TAG,String.format("Warning: The innerClassProxyClass(%s) of a interface must first implements this interface,then implements others!!!",proxyClass.getName()));
                            return cls;
                        }
                    }
                }
                // if the common super class is class
                return proxyClassSuperClass;
            }

        }
        return innerClassSuperClass;
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
        return activity == null || activity.isFinishing() || (AppEnv.AndroidSDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1 && activity.isDestroyed());
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
        Class cls_v4_fragment;
        Class cls_x_fragment;
        @Override
        public boolean isNeedFilter(Field field,Object innerClassInstance) {
            if(innerClassInstance != null && field != null){
                try {
                    if(AppEnv.HasAndroidX && cls_x_fragment == null){
                        try {
                            cls_x_fragment = Class.forName("androidx.fragment.app.Fragment");
                        }catch (Exception e){
                            e.printStackTrace();
                        }
                    }
                    if(AppEnv.HasAndroidSupportLibraryV4 && cls_v4_fragment == null){
                        try {
                            cls_v4_fragment = Class.forName("android.support.v4.app.Fragment");
                        }catch (Exception e){
                            e.printStackTrace();
                        }
                    }
                    field.setAccessible(true);
                    Object obj = field.get(innerClassInstance);
                    if(obj != null){
                        Class type = field.getType();
                        if(AppEnv.IsInAndroidPlatform){
                            if(View.class.isAssignableFrom(type) || Context.class.isAssignableFrom(type)
                                    || (AppEnv.AndroidSDK_INT >= Build.VERSION_CODES.HONEYCOMB && Fragment.class.isAssignableFrom(type))
                                    || Dialog.class.isAssignableFrom(type) || PopupWindow.class.isAssignableFrom(type)
                                    || (cls_x_fragment !=null  && cls_x_fragment.isAssignableFrom(type))
                                    || (cls_v4_fragment !=null  && cls_v4_fragment.isAssignableFrom(type))){
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
        public boolean checkLifeCycleObjectDestroyed(LifeCycleObjectDirectGetter lifeCycleObjectDirectGetter) {
            if(AppEnv.HasAndroidX && cls_x_fragment == null){
                try {
                    cls_v4_fragment = Class.forName("androidx.fragment.app.Fragment");
                }catch (Exception e){
                    e.printStackTrace();
                }
            }
            if(AppEnv.HasAndroidSupportLibraryV4 && cls_v4_fragment == null){
                try {
                    cls_v4_fragment = Class.forName("android.support.v4.app.Fragment");
                }catch (Exception e){
                    e.printStackTrace();
                }
            }
            Object lifeCycleObject = lifeCycleObjectDirectGetter._getLifeCycleObject();
            if(lifeCycleObject == null){
                //developer may be not set
                return false;
            }
            if(AppEnv.IsInAndroidPlatform){
                try {
                    if(Context.class.isInstance(lifeCycleObject) && checkContext((Context) lifeCycleObject)){
                        return true;
                    }
                    if(View.class.isInstance(lifeCycleObject) && checkView((View) lifeCycleObject)){
                        return true;
                    }
                    if(Fragment.class.isInstance(lifeCycleObject)){
                        Fragment fragment = (Fragment) lifeCycleObject;
                        if(checkFragmentState(fragment) || (!checkFragmentNoInLifeCycle(fragment) && checkFragmentState(fragment)) || checkFragmentIsDestroyed(lifeCycleObject)){
                            return true;
                        }
                    }
                    if(cls_x_fragment != null && cls_x_fragment.isInstance(lifeCycleObject) && (checkFragmentCompatState(lifeCycleObject) || (!checkFragmentCompatNoInLifeCycle(lifeCycleObject) && checkFragmentCompatState(lifeCycleObject)) || checkFragmentIsDestroyed(lifeCycleObject))){
                        return true;
                    }
                    if( cls_v4_fragment != null && cls_v4_fragment.isInstance(lifeCycleObject) && (checkFragmentCompatState(lifeCycleObject) || (!checkFragmentCompatNoInLifeCycle(lifeCycleObject) && checkFragmentCompatState(lifeCycleObject)) || checkFragmentIsDestroyed(lifeCycleObject))){
                        return true;
                    }

                    if(Dialog.class.isInstance(lifeCycleObject) && checkDialog((Dialog)lifeCycleObject)){
                        return true;
                    }
                    if(PopupWindow.class.isInstance(lifeCycleObject) && checkPopupWindow((PopupWindow)lifeCycleObject)){
                        return true;
                    }
                }catch (Exception e){
                    e.printStackTrace();
                }
            }
            return false;
        }

        private boolean checkView(View view){
            Context context = view.getContext();
            if(context == null){
                return true;
            }
            Activity activity = getActivityFromContext(context);
            if(activity != null && isActivityDestroyed(activity)){
                return true;
            }
            return false;
        }

        private boolean checkContext(Context context){
            Activity activity = getActivityFromContext(context);
            if(activity != null && isActivityDestroyed(activity)){
                return true;
            }
            return false;
        }

        private boolean checkFragmentNoInLifeCycle(Fragment fragment){
            try {
                return JavaReflectUtils.getField(Fragment.class,"mState").getInt(fragment)<2;
            }catch (Exception e){
                e.printStackTrace();
            }
            return false;
        }

        private boolean checkFragmentIsDestroyed(Object fragment){
            return FragmentDestroyStateGetter.class.isInstance(fragment) && ((FragmentDestroyStateGetter)fragment).isFragmentDestroyed();
        }
        private boolean checkFragmentCompatNoInLifeCycle(Object fragment){
            try {
                if(cls_x_fragment != null){
                    return JavaReflectUtils.getField(cls_x_fragment,"mState").getInt(fragment)<2;
                }
                if(cls_v4_fragment != null){
                    return JavaReflectUtils.getField(cls_v4_fragment,"mState").getInt(fragment)<2;
                }
            }catch (Exception e){
                e.printStackTrace();
            }
            return false;
        }

        private  boolean checkFragmentState(Fragment fragment){
            if(fragment == null || fragment.isRemoving() || fragment.isDetached()){
                return true;
            }
            return false;
        }

        private  boolean checkFragmentCompatState(Object fragment) throws Exception{
            if(cls_x_fragment != null){
                if(fragment == null || (Boolean) JavaReflectUtils.getMethod(cls_x_fragment,"isRemoving").invoke(fragment)  || (Boolean)JavaReflectUtils.getMethod(cls_x_fragment,"isDetached").invoke(fragment)){
                    return true;
                }
            }
            if(cls_v4_fragment != null){
                if(fragment == null || (Boolean) JavaReflectUtils.getMethod(cls_v4_fragment,"isRemoving").invoke(fragment)  || (Boolean)JavaReflectUtils.getMethod(cls_v4_fragment,"isDetached").invoke(fragment)){
                    return true;
                }
            }

            return false;
        }

        private boolean checkFragmentContext(Fragment fragment){
            Context context = fragment.getActivity();
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
            return false;
        }

        private boolean checkFragmentCompatContext(Object fragment) throws Exception{
            if(cls_x_fragment != null){
                Context context = (Context) JavaReflectUtils.getMethod(cls_x_fragment,"getContext").invoke(fragment);
                if(context == null){
                    return true;
                }
                Activity activity = (Activity) JavaReflectUtils.getMethod(cls_x_fragment,"getActivity").invoke(fragment);
                if (isActivityDestroyed(activity)) {
                    return true;
                }
            }
            if(cls_v4_fragment != null){
                Context context = (Context) JavaReflectUtils.getMethod(cls_v4_fragment,"getContext").invoke(fragment);
                if(context == null){
                    return true;
                }
                Activity activity = (Activity) JavaReflectUtils.getMethod(cls_v4_fragment,"getActivity").invoke(fragment);
                if (isActivityDestroyed(activity)) {
                    return true;
                }
            }
            return false;
        }

        private boolean checkDialog(Dialog dialog){
            if(dialog==null || !dialog.isShowing()){
                return true;
            }
            Context context = dialog.getContext();
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
            return false;
        }

        private boolean checkPopupWindow(PopupWindow popupWindow){
            if(popupWindow == null || !popupWindow.isShowing()){
                return true;
            }
            View v = popupWindow.getContentView();
            if(v != null){
                Context context = v.getContext();
                if(context == null){
                    return true;
                }
                Activity activity = getActivityFromContext(context);
                if(activity != null && isActivityDestroyed(activity)){
                    return true;
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
                            if(checkView(view)){
                                return true;
                            }
                        }else if(Context.class.isAssignableFrom(type)){
                            context = (Context) f.get(innerClassInstance);
                            if(context == null){
                                return true;
                            }
                            if(checkContext(context)){
                                return true;
                            }
                        }else if(AppEnv.AndroidSDK_INT>= Build.VERSION_CODES.HONEYCOMB && Fragment.class.isAssignableFrom(type)){
                            Fragment fragment = (Fragment) f.get(innerClassInstance);
                            if(checkFragmentState(fragment)){
                                return true;
                            }
                            if(checkFragmentIsDestroyed(fragment)){
                                return true;
                            }
                            try {
                                //if the fragment is not call onActivityCreate(),pass check it
                                if(checkFragmentNoInLifeCycle(fragment)){
                                    continue;
                                }
                            }catch (Exception e){
                                e.printStackTrace();
                            }
                            if(checkFragmentContext(fragment)){
                                return true;
                            }
                        }else if((cls_x_fragment !=null  && cls_x_fragment.isAssignableFrom(type)) || (cls_v4_fragment !=null  && cls_v4_fragment.isAssignableFrom(type))){
                            Object fragment =  f.get(innerClassInstance);
                            try {
                                if(checkFragmentCompatState(fragment)){
                                    return true;
                                }
                                if(checkFragmentIsDestroyed(fragment)){
                                    return true;
                                }
                                try {
                                    //if the fragment is not call onActivityCreate(),pass check it
                                    if(checkFragmentCompatNoInLifeCycle(fragment)){
                                        continue;
                                    }
                                }catch (Exception e){
                                    e.printStackTrace();
                                }
                                if(checkFragmentCompatContext(fragment)){
                                    return true;
                                }
                            }catch (Exception e){
                                e.printStackTrace();
                            }

                        }else if(Dialog.class.isAssignableFrom(type)){
                            Dialog dialog = (Dialog) f.get(innerClassInstance);
                            if(checkDialog(dialog)){
                                return true;
                            }

                        }else if(PopupWindow.class.isAssignableFrom(type)){
                            PopupWindow popupWindow = (PopupWindow) f.get(innerClassInstance);
                            if(checkPopupWindow(popupWindow)){
                                return true;
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
    public static abstract class ImplicitReferenceChecker implements LifeCycleObjectChecker{
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

        @Override
        public boolean checkLifeCycleObjectDestroyed(LifeCycleObjectDirectGetter lifeCycleObjectDirectGetter) {
            return false;
        }
    }

    /**
     * 有生命周期的对象检测器
     */
    public interface LifeCycleObjectChecker{
        /**
         * 检测监控的生命周期对象是否已经销毁了
         * @param lifeCycleObjectDirectGetter 生命周期对象直接获取器
         * @return
         */
        boolean checkLifeCycleObjectDestroyed(LifeCycleObjectDirectGetter lifeCycleObjectDirectGetter);
    }

    /**
     * 需要检测的防止引发内存泄漏的具有生命周期的对象直接获取器,而不是通过匿名内部类隐式引用属性检测器(ImplicitReferenceChecker)间接去获取该对象,
     * 这种方式有助于提高性能,这在多重匿名内部类中很有用。
     *
     */
    public interface LifeCycleObjectDirectGetter {
        /***
         * 返回需要监控的生命周期对象
         * @return 生命周期的对象
         */
        Object _getLifeCycleObject();

        /**
         * 设置要检测的生命周期对象
         * @param lifeCycleObject 生命周期对象
         */
        void _setLifeCycleObject(Object lifeCycleObject);
    }

    /**
     * 匿名内部类代理类实现接口
     * @param <T> 匿名内部类对象的父类或者父接口
     */
    //https://github.com/BryanSharp/hibeaver java字节码修改神器
    public  interface InnerClassTarget<T>{
        /**
         * 判断当匿名内部类对象在其代理类中清空时是否要清空匿名内部类对象的隐式引用,当匿名内部对象自身存在耗时操作时建议清空其隐式引用，以防出现内存泄漏
         * @return 默认情况下建议返回false
         */
        boolean isNeedClearInnerClassInstanceImplicitReferences();
        /**
         * 判断当匿名内部类对象在其代理类中清空时是否要清空匿名内部类对象的隐式引用,当匿名内部对象自身存在耗时操作时建议清空其隐式引用，以防出现内存泄漏。
         * 同时需要注意的是当isNeedClearInnerClassInstanceImplicitReferences设置为true时，你必须在匿名内部类对象的方法调用中进行异常处理，防止耗时操作执行后引用外部属性或方法抛出空指针异常引发
         * 应用崩溃
         * @param isNeedClearInnerClassInstanceImplicitReferences 默认情况下建议设置为false
         * @return 默认情况下建议返回false
         */
        void setIsNeedClearInnerClassInstanceImplicitReferences(boolean isNeedClearInnerClassInstanceImplicitReferences);
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
     * 在匿名内部类代理对象的方法中处理匿名内部类对象对应的方法
     * @param innerClassTarget  匿名内部类代理对象
     * @param task 匿名内部类对象执行的对应方法中执行的任务
     * @return true 已处理 false 无法处理，因为匿名内部类代理对象所代理的对象已被清空
     */
    public static boolean handleInnerClassInstanceTask(InnerClassTarget innerClassTarget,Runnable task){
        Object innerClassInstance = innerClassTarget.getInnerClassInstance();
        if(innerClassInstance != null){
            synchronized (innerClassTarget){
                if(innerClassTarget.getInnerClassInstance() != null){
                    task.run();
                    return true;
                }else {
                    return false;
                }
            }
        }
        return false;
    }

    public static interface ThrowExceptionTask{
        void call() throws Throwable;
    }

    public static class InnerClassTargetWrapperException extends Exception{
        public InnerClassTargetWrapperException(String message) {
            super(message);
        }

        public InnerClassTargetWrapperException(String message, Throwable cause) {
            super(message, cause);
        }

        public InnerClassTargetWrapperException(Throwable cause) {
            super(cause);
        }

        public <T extends Exception> T getCheckedException(Class<T> guessedThrowExceptionClass){
            Exception exception = (Exception) getCause();
            if(guessedThrowExceptionClass.isInstance(exception)){
                return (T) exception;
            }
            return null;
        }
    }


    /**
     * 在匿名内部类代理对象的方法中处理匿名内部类对象对应的方法,代理方法需要抛异常的请使用这个方法替换handleInnerClassInstanceTask()方法
     * @param innerClassTarget  匿名内部类代理对象
     * @param task 匿名内部类对象执行的对应方法中执行的任务
     * @return true 已处理 false 无法处理，因为匿名内部类代理对象所代理的对象已被清空
     */
    public static boolean handleInnerClassInstanceTaskThrowException(InnerClassTarget innerClassTarget,ThrowExceptionTask task) throws InnerClassTargetWrapperException{
        Object innerClassInstance = innerClassTarget.getInnerClassInstance();
        if(innerClassInstance != null){
            synchronized (innerClassTarget){
                if(innerClassTarget.getInnerClassInstance() != null){
                    try{
                        task.call();
                    }
                    catch (RuntimeException e){
                        throw e;
                    }catch (Error e){
                        throw e;
                    }
                    catch (Throwable e){
                        throw new InnerClassTargetWrapperException(e);
                    }
                    return true;
                }else {
                    return false;
                }
            }
        }
        return false;
    }

    private interface ThrowableMethodProxySample{
        void noThrowException();
        Object noThrowExceptionWithReturnValue();
        void throwError() throws NoClassDefFoundError;
        void throwUnCheckedException() throws NullPointerException;
        void throwCheckedException() throws IOException;
        void throwCheckedException1() throws SQLException;
        void throwsExceptions() throws IOException,NullPointerException,SQLException,NoClassDefFoundError;
    }

    /**
     * 这个样例用于帮助开发者理解 handleInnerClassInstanceTaskThrowException()与  handleInnerClassInstanceTask()的用法,
     * 在代理方法签名中有抛出异常时应该用handleInnerClassInstanceTaskThrowException()，
     * 没有抛出异常时使用 handleInnerClassInstanceTask()。
     */
    public static class ThrowableMethodProxySampleGuidanceExample implements ThrowableMethodProxySample,InnerClassTarget<ThrowableMethodProxySample>,LifeCycleObjectDirectGetter{
        volatile ThrowableMethodProxySample innerClassInstance;
        List<Field> fields;
        ImplicitReferenceChecker implicitReferenceChecker;
        Runnable delayTask;
        Object lifeCycleObject;
        boolean isNeedClearInnerClassInstanceImplicitReferences;
        public ThrowableMethodProxySampleGuidanceExample(ThrowableMethodProxySample innerClassInstance){
            this.innerClassInstance = innerClassInstance;
        }
        @Override
        public boolean isNeedClearInnerClassInstanceImplicitReferences() {
            return isNeedClearInnerClassInstanceImplicitReferences;
        }

        @Override
        public void setIsNeedClearInnerClassInstanceImplicitReferences(boolean isNeedClearInnerClassInstanceImplicitReferences) {
            this.isNeedClearInnerClassInstanceImplicitReferences = isNeedClearInnerClassInstanceImplicitReferences;
        }

        @Override
        public ThrowableMethodProxySample getInnerClassInstance() {
            return innerClassInstance;
        }

        @Override
        public void clearInnerClassInstance() {
            innerClassInstance = null;
            fields = null;
            implicitReferenceChecker = null;
            lifeCycleObject = null;
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
            //保证延迟检测任务只执行一次
            if(delayTask != null){
                delayTask.run();
                delayTask = null;
            }
        }


        @Override
        public Object _getLifeCycleObject() {
            return lifeCycleObject;
        }

        @Override
        public void _setLifeCycleObject(Object lifeCycleObject) {
            this.lifeCycleObject = lifeCycleObject;
        }

        @Override
        public void noThrowException() {
            // noThrowException()没有抛出异常，直接使用handleInnerClassInstanceTask()
            handleInnerClassInstanceTask(this, new Runnable() {
                @Override
                public void run() {
                    innerClassInstance.noThrowException();
                }
            });
        }

        @Override
        public Object noThrowExceptionWithReturnValue() {
            // 代理方法有返回值时可以这么处理
            final Object[] result = {null};
            handleInnerClassInstanceTask(this, new Runnable() {
                @Override
                public void run() {
                    result[0] = innerClassInstance.noThrowExceptionWithReturnValue();
                }
            });
            return result[0];
        }

        @Override
        public void throwError() throws NoClassDefFoundError {
            // throwError() 抛出错误不需要自己处理
            try {
                handleInnerClassInstanceTaskThrowException(this, new ThrowExceptionTask() {
                    @Override
                    public void call() throws Throwable {
                        innerClassInstance.throwError();
                    }
                });
            } catch (InnerClassTargetWrapperException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void throwUnCheckedException() throws NullPointerException {
            // throwUnCheckedException() 抛出非检测异常（主要是运行时异常）不需要自己处理
            try {
                handleInnerClassInstanceTaskThrowException(this, new ThrowExceptionTask() {
                    @Override
                    public void call() throws Throwable {
                        innerClassInstance.throwUnCheckedException();
                    }
                });
            } catch (InnerClassTargetWrapperException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void throwCheckedException() throws IOException {
            // throwCheckedException() 抛出检测型异常（不继承RuntimeException的异常），需要自己处理，如果不自己抛出，这里就会被拦截捕获
            try {
                handleInnerClassInstanceTaskThrowException(this, new ThrowExceptionTask() {
                    @Override
                    public void call() throws Throwable {
                        innerClassInstance.throwCheckedException();
                    }
                });
            } catch (InnerClassTargetWrapperException e) {
                e.printStackTrace();
                IOException checkedException = e.getCheckedException(IOException.class);
                if(checkedException != null){
                    throw checkedException;
                }
            }
        }

        @Override
        public void throwCheckedException1() throws SQLException {
            // throwCheckedException1() 抛出检测型异常（不继承RuntimeException的异常），需要自己处理，如果不自己抛出，这里就会被拦截捕获
            try {
                handleInnerClassInstanceTaskThrowException(this, new ThrowExceptionTask() {
                    @Override
                    public void call() throws Throwable {
                        innerClassInstance.throwCheckedException1();
                    }
                });
            } catch (InnerClassTargetWrapperException e) {
                e.printStackTrace();
                SQLException checkedException = e.getCheckedException(SQLException.class);
                if(checkedException != null){
                    throw checkedException;
                }
            }
        }

        @Override
        public void throwsExceptions() throws IOException, NullPointerException, SQLException, NoClassDefFoundError {
            // throwsExceptions() 方法抛出了错误与异常，只需要处理检测型异常就行
            try {
                handleInnerClassInstanceTaskThrowException(this, new ThrowExceptionTask() {
                    @Override
                    public void call() throws Throwable {
                        innerClassInstance.throwsExceptions();
                    }
                });
            } catch (InnerClassTargetWrapperException e) {
                e.printStackTrace();
                Exception checkedException = e.getCheckedException(SQLException.class);
                if(checkedException != null){
                    throw (SQLException)checkedException;
                }
                checkedException = e.getCheckedException(IOException.class);
                if(checkedException != null){
                    throw (IOException)checkedException;
                }
                // NullPointerException是运行时异常，不需要处理，这里也捕获不到
                // NoClassDefFoundError 所有错误都不需要处理，这里也捕获不到
            }
        }
    }





    /**
     * Runnable的简单的匿名内部类代理类
     */
    @Keep
    public static class SimpleInnerClassProxyClassForRunnable implements Runnable,InnerClassTarget<Runnable>,LifeCycleObjectDirectGetter {
        volatile Runnable innerClassInstance;
        List<Field> fields;
        ImplicitReferenceChecker implicitReferenceChecker;
        Runnable delayTask;
        Object lifeCycleObject;
        public static boolean isTest = false;
        private String innerClassInstanceId;
        @Keep
        public SimpleInnerClassProxyClassForRunnable(Runnable innerClassInstance){
            this.innerClassInstance = innerClassInstance;
            innerClassInstanceId = String.format("%s@%d",innerClassInstance.getClass(),innerClassInstance.hashCode());
        }

        boolean isNeedClearInnerClassInstanceImplicitReferences;
        @Override
        public boolean isNeedClearInnerClassInstanceImplicitReferences() {
            return isNeedClearInnerClassInstanceImplicitReferences;
        }

        @Override
        public void setIsNeedClearInnerClassInstanceImplicitReferences(boolean isNeedClearInnerClassInstanceImplicitReferences) {
            this.isNeedClearInnerClassInstanceImplicitReferences = isNeedClearInnerClassInstanceImplicitReferences;
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
            lifeCycleObject = null;
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
            //保证延迟检测任务只执行一次
            if(delayTask != null){
                delayTask.run();
                delayTask = null;
            }
        }


        @Override
        public Object _getLifeCycleObject() {
            return lifeCycleObject;
        }

        @Override
        public void _setLifeCycleObject(Object lifeCycleObject) {
            this.lifeCycleObject = lifeCycleObject;
        }

        @Override
        public void run() {
            if(!handleInnerClassInstanceTask(this, new Runnable() {
                @Override
                public void run() {
                    innerClassInstance.run();
                }
            })){
                if(isTest){
                    Log.w(TAG,"innerClassInstance已被清空: "+innerClassInstanceId);
                }
            }
        }
    }



    /**
     * Handler的简单的匿名内部类代理类
     */
    @Keep
    public static class SimpleInnerClassProxyClassForHandler extends Handler implements InnerClassTarget<Handler>,LifeCycleObjectDirectGetter {
        List<Field> fields;
        ImplicitReferenceChecker implicitReferenceChecker;
        Runnable delayTask;
        volatile Handler innerClassInstance;
        Object lifeCycleObject;
        @Keep
        public SimpleInnerClassProxyClassForHandler(Handler innerClassInstance){
            super(innerClassInstance.getLooper());
            this.innerClassInstance = innerClassInstance;

        }

        boolean isNeedClearInnerClassInstanceImplicitReferences;
        @Override
        public boolean isNeedClearInnerClassInstanceImplicitReferences() {
            return isNeedClearInnerClassInstanceImplicitReferences;
        }

        @Override
        public void setIsNeedClearInnerClassInstanceImplicitReferences(boolean isNeedClearInnerClassInstanceImplicitReferences) {
            this.isNeedClearInnerClassInstanceImplicitReferences = isNeedClearInnerClassInstanceImplicitReferences;
        }

        @Override
        public Object _getLifeCycleObject() {
            return lifeCycleObject;
        }

        @Override
        public void _setLifeCycleObject(Object lifeCycleObject) {
            this.lifeCycleObject = lifeCycleObject;
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
            lifeCycleObject = null;
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
        public final void notifyNeedCheck() {
            if(delayTask != null){
                delayTask.run();
                delayTask = null;
            }
        }

        @Override
        public void handleMessage(final Message msg) {
            handleInnerClassInstanceTask(this, new Runnable() {
                @Override
                public void run() {
                    innerClassInstance.handleMessage(msg);
                }
            });
        }

        @Override
        public boolean sendMessageAtTime(final Message msg,final long uptimeMillis) {
            final boolean[] result = {false};
            handleInnerClassInstanceTask(this, new Runnable() {
                @Override
                public void run() {
                    result[0] = innerClassInstance.sendMessageAtTime(msg,uptimeMillis);
                }
            });
            return result[0];
        }
    }

    /**
     * BroadcastReceiver的简单的匿名内部类代理类
     */
    @Keep
    public static class SimpleInnerClassProxyClassForBroadcastReceiver extends BroadcastReceiver implements InnerClassTarget<BroadcastReceiver>,LifeCycleObjectDirectGetter {
        volatile BroadcastReceiver innerClassInstance;
        List<Field> fields;
        ImplicitReferenceChecker implicitReferenceChecker;
        Runnable delayTask;
        Object lifeCycleObject;

        boolean isNeedClearInnerClassInstanceImplicitReferences;
        @Override
        public boolean isNeedClearInnerClassInstanceImplicitReferences() {
            return isNeedClearInnerClassInstanceImplicitReferences;
        }

        @Override
        public void setIsNeedClearInnerClassInstanceImplicitReferences(boolean isNeedClearInnerClassInstanceImplicitReferences) {
            this.isNeedClearInnerClassInstanceImplicitReferences = isNeedClearInnerClassInstanceImplicitReferences;
        }

        @Override
        public Object _getLifeCycleObject() {
            return lifeCycleObject;
        }

        @Override
        public void _setLifeCycleObject(Object lifeCycleObject) {
            this.lifeCycleObject = lifeCycleObject;
        }
        @Keep
        public SimpleInnerClassProxyClassForBroadcastReceiver(BroadcastReceiver innerClassInstance){
            this.innerClassInstance = innerClassInstance;
        }
        @Override
        public void onReceive(final Context context,final Intent intent) {
            handleInnerClassInstanceTask(this, new Runnable() {
                @Override
                public void run() {
                    innerClassInstance.onReceive(context,intent);
                }
            });
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
        public final void notifyNeedCheck() {
            if(delayTask != null){
                delayTask.run();
                delayTask = null;
            }
        }
    }

    /**
     * 用于有参回调函数
     * @param <T>
     */
    public static interface Callback<T>{
        void call(String action,T data);
    }
    /**
     * Callback的简单的匿名内部类代理类
     */
    @Keep
    public static class SimpleInnerClassProxyClassForCallback<T> implements Callback<T>,InnerClassTarget<Callback<T>>,LifeCycleObjectDirectGetter {
        volatile Callback<T> innerClassInstance;
        List<Field> fields;
        ImplicitReferenceChecker implicitReferenceChecker;
        Runnable delayTask;
        Object lifeCycleObject;
        public static boolean isTest = false;
        private String innerClassInstanceId;
        @Keep
        public SimpleInnerClassProxyClassForCallback(Callback<T> innerClassInstance){
            this.innerClassInstance = innerClassInstance;
            innerClassInstanceId = String.format("%s@%d",innerClassInstance.getClass(),innerClassInstance.hashCode());
        }

        boolean isNeedClearInnerClassInstanceImplicitReferences;
        @Override
        public boolean isNeedClearInnerClassInstanceImplicitReferences() {
            return isNeedClearInnerClassInstanceImplicitReferences;
        }

        @Override
        public void setIsNeedClearInnerClassInstanceImplicitReferences(boolean isNeedClearInnerClassInstanceImplicitReferences) {
            this.isNeedClearInnerClassInstanceImplicitReferences = isNeedClearInnerClassInstanceImplicitReferences;
        }

        @Override
        public Callback<T> getInnerClassInstance() {
            return innerClassInstance;
        }

        @Override
        public void clearInnerClassInstance() {
            innerClassInstance = null;
            fields = null;
            implicitReferenceChecker = null;
            lifeCycleObject = null;
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
            //保证延迟检测任务只执行一次
            if(delayTask != null){
                delayTask.run();
                delayTask = null;
            }
        }


        @Override
        public Object _getLifeCycleObject() {
            return lifeCycleObject;
        }

        @Override
        public void _setLifeCycleObject(Object lifeCycleObject) {
            this.lifeCycleObject = lifeCycleObject;
        }

        @Override
        public void call(final String action,final T data) {
            if(!handleInnerClassInstanceTask(this, new Runnable() {
                @Override
                public void run() {
                    innerClassInstance.call(action,data);
                }
            })){
                if(isTest){
                    Log.w(TAG,"innerClassInstance已被清空: "+innerClassInstanceId);
                }
            }
        }
    }    

    static {
        if(AppEnv.IsInAndroidPlatform){
            registerSupperClassOfInnerClassProxyClass(SimpleInnerClassProxyClassForBroadcastReceiver.class);
            registerSupperClassOfInnerClassProxyClass(SimpleInnerClassProxyClassForHandler.class);
        }
        registerSupperClassOfInnerClassProxyClass(SimpleInnerClassProxyClassForRunnable.class);
        registerSupperClassOfInnerClassProxyClass(SimpleInnerClassProxyClassForCallback.class);
    }
}
