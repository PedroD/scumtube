package com.backyt;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.IBinder;
import android.support.v4.app.NotificationCompat;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.widget.RemoteViews;

import java.io.IOException;


public class PlayerService extends Service {

    public static final String APP_NAME
            = "Backyt";

    public static final String ACTION_PLAYPAUSE
            = "com.backyt.ACTION_PLAYPAUSE";

    public static final String ACTION_PLAY
            = "com.backyt.ACTION_PLAY";

    public static final String ACTION_PAUSE
            = "com.backyt.ACTION_PAUSE";

    public static final String ACTION_EXIT
            = "com.backyt.ACTION_EXIT";

    private static final int PLAYBACKSERVICE_PLAYSTATE_PLAYING = 0;

    private static final int PLAYBACKSERVICE_PLAYSTATE_PAUSED = 1;

    private static final int PLAYERSERVICE_NOTIFICATION_ID = 1;

    private int mPlayState = PLAYBACKSERVICE_PLAYSTATE_PLAYING;

    private boolean mShowingNotification;

    private Notification mNotification;

    private RemoteViews mSmallNotificationView;

    private RemoteViews mLargeNotificationView;

    private MediaPlayer mMediaPlayer = new MediaPlayer();

    private PhoneCallListener mPhoneCallListener = new PhoneCallListener();

    private String mVideoTitle;

    public PlayerService() {
    }

    @Override
    public void onCreate() {
        super.onCreate();

        // Initialize PhoneCallListener
        TelephonyManager telephonyManager =
                (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
        telephonyManager.listen(mPhoneCallListener, PhoneStateListener.LISTEN_CALL_STATE);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && intent.getAction() != null) {
            if (intent.getAction().equals(ACTION_PLAYPAUSE)) {
                playPause();
            } else if (intent.getAction().equals(ACTION_PLAY)) {
                start();
            } else if (intent.getAction().equals(ACTION_PAUSE)) {
                pause();
            } else if (intent.getAction().equals(ACTION_EXIT)) {
                pause(true);
            }
        }
        return START_STICKY;
    }

    public void start() {
        mMediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
        try {
            mMediaPlayer.setDataSource(this, Uri.parse("")); //TODO URL
        } catch (IOException e) {
            e.printStackTrace();
        }
        try {
            mMediaPlayer.prepare(); // might take long! (for buffering, etc)
        } catch (IOException e) {
            e.printStackTrace();
        }
        mMediaPlayer.start();

        mShowingNotification = true;
        updateNotification();
    }

    public void pause() {

    }

    public void pause(boolean b) {

    }

    public void playPause() {

    }

    public void updateNotification() {
        if (mShowingNotification) {

            Intent intent = new Intent(ACTION_PLAYPAUSE, null, PlayerService.this,
                    PlayerService.class);
            PendingIntent playPausePendingIntent = PendingIntent
                    .getService(PlayerService.this, 0, intent,
                            PendingIntent.FLAG_UPDATE_CURRENT);

            intent = new Intent(ACTION_EXIT, null, PlayerService.this, PlayerService.class);
            PendingIntent exitPendingIntent = PendingIntent
                    .getService(PlayerService.this, 0, intent,
                            PendingIntent.FLAG_UPDATE_CURRENT);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                mSmallNotificationView = new RemoteViews(getPackageName(),
                        R.layout.notification_small);
            } else {
                mSmallNotificationView = new RemoteViews(getPackageName(),
                        R.layout.notification_small_compat);
            }
            mSmallNotificationView
                    .setTextViewText(R.id.notification_small_textview, APP_NAME);
            mSmallNotificationView.setTextViewText(R.id.notification_small_textview2,
                    mVideoTitle);
            if (isPlaying()) {
                mSmallNotificationView
                        .setImageViewResource(R.id.notification_small_imageview_playpause,
                                R.drawable.ic_player_pause_light);
            } else {
                mSmallNotificationView
                        .setImageViewResource(R.id.notification_small_imageview_playpause,
                                R.drawable.ic_player_play_light);
            }
            mSmallNotificationView
                    .setOnClickPendingIntent(R.id.notification_small_imageview_playpause,
                            playPausePendingIntent);
            mSmallNotificationView
                    .setOnClickPendingIntent(R.id.notification_small_imageview_exit,
                            exitPendingIntent);

            NotificationCompat.Builder builder = new NotificationCompat.Builder(
                    PlayerService.this)
                    .setSmallIcon(R.drawable.ic_notification).setContentTitle(APP_NAME)
                    .setContentText(mVideoTitle).setOngoing(true).setPriority(
                            NotificationCompat.PRIORITY_MAX).setContent(mSmallNotificationView);

            mNotification = builder.build();

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                mLargeNotificationView = new RemoteViews(getPackageName(),
                        R.layout.notification_large);
                mLargeNotificationView.setTextViewText(R.id.notification_large_textview,
                        APP_NAME);
                mLargeNotificationView
                        .setTextViewText(R.id.notification_large_textview2, mVideoTitle);
                if (isPlaying()) {
                    mLargeNotificationView
                            .setImageViewResource(R.id.notification_large_imageview_playpause,
                                    R.drawable.ic_player_pause_light);
                } else {
                    mLargeNotificationView
                            .setImageViewResource(R.id.notification_large_imageview_playpause,
                                    R.drawable.ic_player_play_light);
                }
                mLargeNotificationView
                        .setOnClickPendingIntent(R.id.notification_large_imageview_playpause,
                                playPausePendingIntent);
                mLargeNotificationView
                        .setOnClickPendingIntent(R.id.notification_large_imageview_exit,
                                exitPendingIntent);
                mNotification.bigContentView = mLargeNotificationView;

                /*new Handler(Looper.getMainLooper()).post(new Runnable() {
                    @Override
                    public void run() {
                        TomahawkUtils.loadImageIntoNotification(TomahawkApp.getContext(),
                                getCurrentQuery().getImage(), mSmallNotificationView,
                                R.id.notification_small_imageview_albumart,
                                PLAYBACKSERVICE_NOTIFICATION_ID,
                                mNotification, Image.getSmallImageSize(),
                                getCurrentQuery().hasArtistImage());
                        TomahawkUtils.loadImageIntoNotification(TomahawkApp.getContext(),
                                getCurrentQuery().getImage(), mLargeNotificationView,
                                R.id.notification_large_imageview_albumart,
                                PLAYBACKSERVICE_NOTIFICATION_ID,
                                mNotification, Image.getSmallImageSize(),
                                getCurrentQuery().hasArtistImage());
                    }
                });*/
            }
            startForeground(PLAYERSERVICE_NOTIFICATION_ID, mNotification);
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        // TODO: Return the communication channel to the service.
        throw new UnsupportedOperationException("Not yet implemented");
    }

    private class PhoneCallListener extends PhoneStateListener {

        private long mStartCallTime = 0L;

        @Override
        public void onCallStateChanged(int state, String incomingNumber) {
            switch (state) {
                case TelephonyManager.CALL_STATE_RINGING:
                    if (isPlaying()) {
                        mStartCallTime = System.currentTimeMillis();
                        pause();
                    }
                    break;

                case TelephonyManager.CALL_STATE_IDLE:
                    if (mStartCallTime > 0 && (System.currentTimeMillis() - mStartCallTime
                            < 30000)) {
                        start();
                    }

                    mStartCallTime = 0L;
                    break;
            }
        }
    }

    public boolean isPlaying() {
        return mPlayState == PLAYBACKSERVICE_PLAYSTATE_PLAYING;
    }
}
