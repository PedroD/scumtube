package com.soundtape;

import android.app.Application;
import android.widget.RemoteViews;

import java.io.UnsupportedEncodingException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class ScumTubeApplication extends Application {

    public static final String APP_NAME = "ScumTube";
    public static final String TAG = "ScumTubeLog";
    public static final String _T = "57eeec0a6974ecb4e9fcf68fab052f7b";

    public static final String PREFS_NAME = "scumtube_preferences";
    public static final String PREFS_MODE_MUSIC = "mode_music";
    public static final String PREFS_MODE_PLAYLIST = "mode_playlist";
    public static final String PREFS_MUSICLIST = "MusicList";

    public static final String TYPE_PLAYLIST = "playlist";
    public static final String TYPE_MUSIC = "music";

    public static RemoteViews mSmallNotificationView;
    public static RemoteViews mLargeNotificationView;
    public static RemoteViews mSmallLoadingNotificationView;

    @Override
    public void onCreate() {
        super.onCreate();
        final Thread.UncaughtExceptionHandler androidDefaultUEH = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler(new UncaughtExceptionReporter(androidDefaultUEH));

        mSmallLoadingNotificationView = new RemoteViews(getPackageName(), R.layout.notification_loading);


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

    public static String parseVideoId(String url) {
        //https://youtu.be/astSQRh1-i0
        String[] splittedUrl = url.split("/");
        return splittedUrl[3]; //splittedUrl[3] is the id of the video
    }

    public static String parsePlaylistId(String url) {
        //http://www.youtube.com/playlist?list=PLiAXHITvMgI_wAyCqda0LQXPRpAYOEd0Y
        String[] splittedUrl = url.split("list=");
        return splittedUrl[1]; //splittedUrl[3] is the id of the video
    }

}
