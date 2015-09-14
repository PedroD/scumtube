package com.soundtape;

import android.app.Application;
import android.content.SharedPreferences;
import android.os.Environment;
import android.widget.RemoteViews;

import com.google.android.gms.analytics.GoogleAnalytics;
import com.google.android.gms.analytics.Tracker;

import java.io.UnsupportedEncodingException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class SoundtapeApplication extends Application {

    public static final String APP_NAME = "SoundTape";
    public static final String TAG = "soundtapeLog";
    public static final String _T = "57eeec0a6974ecb4e9fcf68fab052f7b";

    public static final String PREFS_NAME = "soundtape_preferences";
    public static final String PREFS_MODE_MUSIC = "mode_music";
    public static final String PREFS_MODE_PLAYLIST = "mode_playlist";
    public static final String PREFS_MUSICLIST = "MusicList";
    public static final String PREFS_DOWNLOAD_DIRECTORY = "downloadDirectory";

    public static final String TYPE_PLAYLIST = "playlist";
    public static final String TYPE_MUSIC = "music";

    public static String downloadDirectory;

    public static RemoteViews mSmallNotificationView;
    public static RemoteViews mLargeNotificationView;
    public static RemoteViews mSmallLoadingNotificationView;

    private Tracker mTracker;

    private boolean hasCreatedIcon;

    @Override
    public void onCreate() {
        super.onCreate();
        final Thread.UncaughtExceptionHandler androidDefaultUEH = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler(new UncaughtExceptionReporter(androidDefaultUEH));
        loadDownloadDirectory();
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

    private void loadDownloadDirectory() {
        SharedPreferences preferences = getSharedPreferences(SoundtapeApplication.PREFS_NAME, 0);
        downloadDirectory = preferences.getString(SoundtapeApplication.PREFS_DOWNLOAD_DIRECTORY, Environment.getExternalStorageDirectory() + "/soundtape/");
    }

    synchronized public Tracker getDefaultTracker() {
        if (mTracker == null) {
            GoogleAnalytics analytics = GoogleAnalytics.getInstance(this);
            // To enable debug logging use: adb shell setprop log.tag.GAv4 DEBUG
            mTracker = analytics.newTracker(R.xml.global_tracker);
        }
        return mTracker;
    }

}
