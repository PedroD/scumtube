package com.soundtape;

public final class UncaughtExceptionReporter implements Thread.UncaughtExceptionHandler {

    private Thread.UncaughtExceptionHandler androidDefaultUEH;

    public UncaughtExceptionReporter(Thread.UncaughtExceptionHandler androidDefaultUEH) {
        this.androidDefaultUEH = androidDefaultUEH;
    }

    @Override
    public void uncaughtException(final Thread paramThread, final Throwable paramThrowable) {
        Logger.e(SoundtapeApplication.TAG, "Severe error: " + paramThrowable.getClass().getName(), paramThrowable);
        androidDefaultUEH.uncaughtException(paramThread, paramThrowable);
        System.exit(2);
    }
}
