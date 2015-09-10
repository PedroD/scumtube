package com.soundtape;

import android.util.Log;

/**
 * Created by Mestre on 28/08/2015.
 */
public class Logger {

    private static final boolean DEBUG = false;

    public static void i(String tag, String msg) {
        if (DEBUG)
            Log.i(tag, msg);
    }

    public static void d(String tag, String msg) {
        if (DEBUG)
            Log.d(tag, msg);
    }

    public static void e(String tag, String msg) {
        if (DEBUG)
            Log.e(tag, msg);
    }

    public static void e(String tag, String msg, Throwable e) {
        if (DEBUG)
            Log.e(tag, msg, e);
    }

    public static void i(String tag, String msg, Throwable e) {
        if (DEBUG)
            Log.i(tag, msg, e);
    }

    public static void w(String tag, String msg) {
        if (DEBUG)
            Log.w(tag, msg);
    }
}
