package com.scumtube;

import android.app.Application;
import android.os.Build;
import android.widget.RemoteViews;

import java.io.UnsupportedEncodingException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class ScumTubeApplication extends Application {

    public static final String APP_NAME = "ScumTube";
    public static final String TAG = "ScumTubeLog";
    public static final String _T = "91c85f899e56014969935fefd68830b9";

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

        mSmallLoadingNotificationView = new RemoteViews(getPackageName(), R.layout.notification_loading);

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

    public static String md5(String string) {
        byte[] hash;
        try {
            hash = MessageDigest.getInstance("MD5").digest(string.getBytes("UTF-8"));
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Huh, MD5 should be supported?", e);
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException("Huh, UTF-8 should be supported?", e);
        }
        StringBuilder hex = new StringBuilder(hash.length * 2);
        for (byte b : hash) {
            int i = (b & 0xFF);
            if (i < 0x10) hex.append('0');
            hex.append(Integer.toHexString(i));
        }
        return hex.toString();
    }

}
