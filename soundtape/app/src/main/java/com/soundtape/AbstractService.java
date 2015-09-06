package com.soundtape;

import android.app.Service;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

public abstract class AbstractService extends Service {
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
