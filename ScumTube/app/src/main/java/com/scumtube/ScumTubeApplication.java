package com.scumtube;

import android.app.Application;
import android.os.Build;
import android.widget.RemoteViews;

public class ScumTubeApplication extends Application {

    public static final String APP_NAME = "ScumTube";
    public static final String TAG = "ScumTubeLog";

    public static final String PREFS_NAME = "scumtube_preferences";
    public static final String PREFS_ISLOOPING = "isLooping";
    public static final String PREFS_MUSICLIST = "MusicList";

    public static RemoteViews mSmallNotificationView;
    public static RemoteViews mLargeNotificationView;
    public static RemoteViews mSmallLoadingNotificationView;

    @Override
    public void onCreate() {
        super.onCreate();
        final Thread.UncaughtExceptionHandler androidDefaultUEH = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler(new UncaughtExceptionReporter(androidDefaultUEH));

        mSmallLoadingNotificationView = new RemoteViews(getPackageName(), R.layout.notification_loading_small);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            mLargeNotificationView = new RemoteViews(getPackageName(),
                    R.layout.notification_large);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            mSmallNotificationView = new RemoteViews(getPackageName(),
                    R.layout.notification_small);
        } else {
            mSmallNotificationView = new RemoteViews(getPackageName(),
                    R.layout.notification_small_compat);
        }
    }
}
