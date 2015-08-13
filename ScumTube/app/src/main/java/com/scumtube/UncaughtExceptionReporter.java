package com.scumtube;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

public final class UncaughtExceptionReporter implements Thread.UncaughtExceptionHandler {

    @Override
    public void uncaughtException(Thread paramThread, Throwable paramThrowable) {
        Log.e(Core.TAG, paramThrowable.getClass().getName(), paramThrowable);
        new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
            @Override
            public void run() {
                System.exit(1);
            }
        }, 5000);
    }
}
