package com.github.carlos2927.javamemoryleakfixer;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.support.annotation.Nullable;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import com.carlos2927.java.memoryleakfixer.InnerClassHelper;
import com.carlos2927.java.memoryleakfixer.Log;

import java.util.ArrayList;
import java.util.List;

public class TestHandleInnerClassInstanceTaskActivity extends Activity {
    private static volatile boolean TestIsDestroyed = false;
    private static Handler TestHandler;
    private EditText et_testCount;
    private Button btn_test;
    private TextView tv_exception_count,tv_normal_count;
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_test_handle_innerclass_instance);
        InnerClassHelper.SimpleInnerClassProxyClassForRunnable.isTest = true;
        et_testCount = findViewById(R.id.et_testCount);
        btn_test = findViewById(R.id.btn_test);
        tv_exception_count = findViewById(R.id.tv_exception_count);
        tv_normal_count = findViewById(R.id.tv_normal_count);
        btn_test.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    tv_normal_count.setText("0");
                    tv_exception_count.setText("0");
                    final int count =  Integer.valueOf(et_testCount.getText().toString().trim());
                    btn_test.setEnabled(false);
                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            new TestThread(generateTasks(count)).start();
                        }
                    }).start();
                }catch (Exception e){
                    e.printStackTrace();
                }
            }
        });
        TestThread.isRunning = true;
        TestHandler = new Handler(Looper.getMainLooper()){
            @Override
            public void handleMessage(Message msg) {
                switch (msg.what){
                    case 1:
                        tv_normal_count.setText(String.valueOf(msg.arg1));
                        break;
                    case 2:
                        tv_exception_count.setText(String.valueOf(msg.arg1));
                        break;
                    case 3:
                        btn_test.setEnabled(true);
                        break;
                    default:
                        super.handleMessage(msg);
                        break;
                }
            }
        };
    }

    @Override
    public boolean isDestroyed() {
        return TestIsDestroyed || super.isDestroyed();
    }

    @Override
    protected void onDestroy() {
        TestThread.isRunning = false;
        TestHandler = null;
        super.onDestroy();
    }

    private List<Runnable> generateTasks(int count){
        List<Runnable> tasks = new ArrayList<>();
        for(int i=0;i<count;i++){
            final int index = i;
            Runnable task = InnerClassHelper.createProxyInnerClassInstance(true,this,new Runnable(){
                @Override
                public void run() {
                    // 如果activity被销毁 当前对象隐式引用会被清空 btn_test为null
                    // 下面的打印语句会报空指针异常
                    Log.i("gyTest","run "+index +"  "+btn_test);
                }
            });
            tasks.add(task);
            Log.d("gyTest",String.format("add a task %d",index));
        }
        return tasks;
    }

    private static class TestThread extends Thread{
        static boolean isRunning = true;
        private List<Runnable> tasks;
        public TestThread(List<Runnable> tasks){
            this.tasks = tasks;
        }
        @Override
        public void run() {
            int errorCount = 0;
            int normalCount = 0;
            for(int i = tasks.size()-1;i>=0;i--){
                if(!isRunning){
                    return;
                }
                Runnable task = tasks.get(i);
                TestIsDestroyed = true;// 模拟activity被销毁
//                try {
//                    sleep(5);
//                } catch (InterruptedException e) {
//                    e.printStackTrace();
//                }
                try {
                    Log.i("gyTest","start run "+i);
                    task.run();
                    normalCount++;
                    TestHandler.sendMessageDelayed(Message.obtain(TestHandler,1,normalCount,0),10);
                }catch (Exception e){
                    e.printStackTrace();
                    errorCount++;
                    TestHandler.sendMessageDelayed(Message.obtain(TestHandler,2,errorCount,0),10);
                }
//                try {
//                    sleep(2);
//                } catch (InterruptedException e) {
//                    e.printStackTrace();
//                }
                TestIsDestroyed = false;
            }
            TestHandler.sendEmptyMessage(3);
        }
    }
}
