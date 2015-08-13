package com.scumtube;

import android.app.Activity;
import android.app.Service;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

public abstract class AbstractService extends Service {
    @Override
    public void onCreate() {
        super.onCreate();
        /**
         * Handle all uncaught exceptions.
         */
        Thread.setDefaultUncaughtExceptionHandler(new UncaughtExceptionReporter());
    }

    protected void showToast(final String message) {
        Handler handler = new Handler(Looper.getMainLooper());
        handler.post(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(AbstractService.this.getApplicationContext(), message, Toast.LENGTH_LONG).show();
            }
        });
    }
}
