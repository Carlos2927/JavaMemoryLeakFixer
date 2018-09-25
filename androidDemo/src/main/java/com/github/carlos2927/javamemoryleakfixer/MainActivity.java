package com.github.carlos2927.javamemoryleakfixer;

import android.app.Activity;
import android.support.v4.util.Pools;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.InputFilter;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.method.ReplacementTransformationMethod;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.EditText;
import android.widget.TextView;

import com.carlos2927.java.memoryleakfixer.AppEnv;
import com.carlos2927.java.memoryleakfixer.InnerClassHelper;
import com.carlos2927.java.memoryleakfixer.JavaMemoryLeakFixer;
import com.carlos2927.java.memoryleakfixer.JavaReflectUtils;
import com.carlos2927.java.memoryleakfixer.Log;
import com.carlos2927.java.memoryleakfixer.Watchable;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;

public class MainActivity extends AppCompatActivity {
    static {
        JavaMemoryLeakFixer.startWatchJavaMemory();
        JavaMemoryLeakFixer.addWatchingClass(AccessibilityNodeInfo.class, new Watchable() {
            Class cls;
            Class cls_Editor$UndoInputFilter;
            Class cls_Editor;
            Class cls_TextView$ChangeWatcher;
            List<AccessibilityNodeInfo> accessibilityNodeInfoList = new ArrayList<>();
            private CharSequence getOriginalText(AccessibilityNodeInfo accessibilityNodeInfo){
                Method method = JavaReflectUtils.getMethod(AccessibilityNodeInfo.class,"getOriginalText");
                if(method != null){
                    try {
                        return (CharSequence) method.invoke(accessibilityNodeInfo);
                    }catch (Exception e){
                        e.printStackTrace();
                    }
                }
                Field field = JavaReflectUtils.getField(AccessibilityNodeInfo.class,"mOriginalText");
                if(field != null){
                    try {
                        return (CharSequence) field.get(accessibilityNodeInfo);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
                return null;
            }

            boolean isNeedRelease(CharSequence charSequence){
                if(cls == null){
                    try {
                        cls = Class.forName("android.text.method.ReplacementTransformationMethod$SpannedReplacementCharSequence");
                    } catch (ClassNotFoundException e) {
                        e.printStackTrace();
                    }
                }
                if(cls.isInstance(charSequence)){
                    try {
                        Spanned spanned = (Spanned) JavaReflectUtils.getField(cls,"mSpanned").get(charSequence);
                        if(spanned instanceof SpannableStringBuilder){
                            Activity activity = null;
                            SpannableStringBuilder spannableStringBuilder = (SpannableStringBuilder) spanned;
                            InputFilter[] filters = spannableStringBuilder.getFilters();
                            if(filters != null){
                                if(cls_Editor$UndoInputFilter == null){
                                    try {
                                        cls_Editor$UndoInputFilter = Class.forName("android.widget.Editor$UndoInputFilter");
                                        for(InputFilter inputFilter:filters){
                                            if(cls_Editor$UndoInputFilter.isInstance(inputFilter)){
                                                try {
                                                    Object mEditor = JavaReflectUtils.getField(cls_Editor$UndoInputFilter,"mEditor").get(inputFilter);
                                                    if(mEditor != null){
                                                        if(cls_Editor == null){
                                                            cls_Editor = Class.forName("android.widget.Editor");
                                                        }
                                                        TextView textView = (TextView) JavaReflectUtils.getField(cls_Editor,"mTextView").get(mEditor);
                                                        if(textView != null){
                                                            activity = InnerClassHelper.getActivityFromContext(textView.getContext());
                                                            break;
                                                        }
                                                    }
                                                }catch (Exception e){
                                                    e.printStackTrace();
                                                }
                                            }
                                        }
                                    } catch (Exception e) {
                                        e.printStackTrace();
                                    }
                                }
                            }
                            if(activity == null){
                                try {
                                    Object[] mSpans = (Object[]) JavaReflectUtils.getField(SpannableStringBuilder.class,"mSpans").get(spannableStringBuilder);
                                    if(cls_TextView$ChangeWatcher == null){
                                        cls_TextView$ChangeWatcher = Class.forName("android.widget.TextView$ChangeWatcher");
                                    }
                                    for(Object obj:mSpans){
                                        if(cls_TextView$ChangeWatcher.isInstance(obj)){
                                            List<Field> fieldList = InnerClassHelper.getSyntheticFields(cls_TextView$ChangeWatcher);
                                            if(fieldList != null){
                                                for(Field field:fieldList){
                                                    field.setAccessible(true);
                                                    if(TextView.class.isInstance(field.get(obj))){
                                                        TextView textView = (TextView)field.get(obj);
                                                        activity = InnerClassHelper.getActivityFromContext(textView.getContext());
                                                        break;
                                                    }
                                                }
                                                if(activity != null){
                                                    break;
                                                }
                                            }
                                        }
                                    }
                                }catch (Exception e){
                                    e.printStackTrace();
                                }

                            }

                            if(activity != null){
                                return InnerClassHelper.isActivityDestroyed(activity);
                            }

                        }
                    } catch (IllegalAccessException e) {
                        e.printStackTrace();
                    }
                }
                return false;
            }

            @Override
            public void watch() {
                for(int i = 0;i<50;i++){
                    AccessibilityNodeInfo accessibilityNodeInfo = AccessibilityNodeInfo.obtain();
                    CharSequence charSequence = getOriginalText(accessibilityNodeInfo);
                    if(charSequence == null){
                        break;
                    }
                    if(isNeedRelease(charSequence)){
                        accessibilityNodeInfo.setText(null);
                    }
                    accessibilityNodeInfoList.add(accessibilityNodeInfo);
                }
                for(AccessibilityNodeInfo accessibilityNodeInfo:accessibilityNodeInfoList){
                    accessibilityNodeInfo.recycle();
                }
                accessibilityNodeInfoList.clear();

            }
        });
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        AppEnv.setInnerClassHelperLoopCheckingThreadFindEmptyListSleepDuration(100);

        final Runnable task = InnerClassHelper.createProxyInnerClassInstance(new Runnable() {
            @Override
            public void run() {
                Log.e("test","test JavaMemoryLeakFixer");
                try {
                    finish();
                    Thread.sleep(5000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        });
        new Thread(task).start();
    }
}
