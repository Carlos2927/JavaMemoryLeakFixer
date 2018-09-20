package com.github.carlos2927.javamemoryleakfixer;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;

import com.carlos2927.java.memoryleakfixer.AppEnv;
import com.carlos2927.java.memoryleakfixer.InnerClassHelper;
import com.carlos2927.java.memoryleakfixer.Log;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        System.setProperty("IsUseInJavaMemoryLeakFixSourceTest","true");
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
