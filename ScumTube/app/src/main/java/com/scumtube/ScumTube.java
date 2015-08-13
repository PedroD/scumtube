package com.scumtube;

import android.app.Application;

/**
 * Created by Mestre on 13/08/2015.
 */
public class ScumTube extends Application {

    public static final String APP_NAME = "ScumTube";
    public static final String TAG = "ScumTubeLog";

    @Override

    public void onCreate() {
        super.onCreate();
        final Thread.UncaughtExceptionHandler androidDefaultUEH = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler(new UncaughtExceptionReporter(androidDefaultUEH));
    }

}
