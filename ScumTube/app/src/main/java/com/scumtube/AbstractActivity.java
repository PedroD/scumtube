package com.scumtube;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

public class AbstractActivity extends Activity {

    protected void showToast( final String message) {
        Handler handler = new Handler(Looper.getMainLooper());
        handler.post(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(AbstractActivity.this.getApplicationContext(), message, Toast.LENGTH_LONG).show();
            }
        });
    }
}
