package com.scumtube;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.IBinder;
import android.support.v4.app.NotificationCompat;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.widget.Toast;

import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.locks.ReentrantLock;

import static com.scumtube.ScumTubeApplication.mLargeNotificationView;
import static com.scumtube.ScumTubeApplication.mSmallLoadingNotificationView;
import static com.scumtube.ScumTubeApplication.mSmallNotificationView;

public class PlayerService extends AbstractService {

    private static final String ACTION_PLAYPAUSE = "com.scumtube.ACTION_PLAYPAUSE";
    private static final String ACTION_PLAY = "com.scumtube.ACTION_PLAY";
    private static final String ACTION_PAUSE = "com.scumtube.ACTION_PAUSE";
    private static final String ACTION_EXIT = "com.scumtube.ACTION_EXIT";
    private static final String ACTION_EXITLOADING = "com.scumtube.ACTION_EXITLOADING";
    private static final String ACTION_LOOP = "com.scumtube.ACTION_LOOP";
    private static final String ACTION_DOWNLOAD = "com.scumtube.ACTION_DOWNLOAD";


    public static final String EXTRA_DATASETCHANGED = "Data Set Changed";

    private static final int PLAYERSERVICE_NOTIFICATION_ID = 1;
    private String sStreamMp3Url;
    private String sStreamCoverUrl;
    private String sStreamTitle;
    private Bitmap sCover;
    private String sYtVideoId;
    private String sYtUrl;
    private final MediaPlayer mMediaPlayer = new MediaPlayer();
    private final PhoneCallListener mPhoneCallListener = new PhoneCallListener();
    private final AudioManagerListener mAudioFocusListener = new AudioManagerListener();
    private Notification mNotification;
    private Thread requestMp3 = null;
    private Object canExitLock = new ReentrantLock();

    public PlayerService() {
    }

    @Override
    public void onCreate() {
        Logger.i(ScumTubeApplication.TAG, "On create invoked.");
        super.onCreate();

        // Initialize PhoneCallListener
        TelephonyManager telephonyManager =
                (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
        telephonyManager.listen(mPhoneCallListener, PhoneStateListener.LISTEN_CALL_STATE);

        mMediaPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
            @Override
            public void onCompletion(MediaPlayer mp) {
                drawPlayPause();
            }
        });
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Logger.i(ScumTubeApplication.TAG, "On start command invoked: " + intent);

        synchronized (canExitLock) {
            if (intent.getExtras() != null) {
                if (requestMp3 != null) {
                    Logger.i(ScumTubeApplication.TAG, "Killing previous download requestMp3Task.");
                    requestMp3.interrupt();
                    requestMp3 = null;
                }
                if (mMediaPlayer.isPlaying()) {
                    mMediaPlayer.stop();
                }
                mMediaPlayer.reset();
                createLoadingNotification();
                sYtUrl = intent.getExtras().getString("ytUrl");
                sYtVideoId = ScumTubeApplication.parseVideoId(sYtUrl);
                requestMp3 = new RequestMp3(sYtVideoId);
                requestMp3.start();
            } else if (intent.getAction().equals(ACTION_EXITLOADING)) {
                exit();
            } else if (intent.getAction().equals(ACTION_PLAYPAUSE)) {
                playPause();
            } else if (intent.getAction().equals(ACTION_PLAY)) {
                start();
            } else if (intent.getAction().equals(ACTION_LOOP)) {
                loop();
            } else if (intent.getAction().equals(ACTION_EXIT)) {
                exit();
            } else if (intent.getAction().equals(ACTION_DOWNLOAD)) {
                download();
            } else {
                Toast.makeText(getApplicationContext(), "An error occurred while accessing the video!", Toast.LENGTH_LONG).show();
            }
        }
        return START_NOT_STICKY;
    }

    public void start() {
        try {
            mMediaPlayer.reset();
            mMediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
            mMediaPlayer.setDataSource(this, Uri.parse(sStreamMp3Url));
        } catch (Exception e) {
            e.printStackTrace();
        }
        try {
            mMediaPlayer.prepare(); // might take long! (for buffering, etc)
        } catch (IOException e) {
            e.printStackTrace();
        }
        mMediaPlayer.start();

        loadLoopPreferencesFromStorage();

        createNotification();

        /*
         * Detect when videos start playing in the background to lower or stop the music.
         */
        final AudioManager am = (AudioManager) getApplicationContext()
                .getSystemService(Context.AUDIO_SERVICE);
        // Request audio focus for play back
        final int result = am.requestAudioFocus(mAudioFocusListener,
                // Use the music stream.
                AudioManager.STREAM_MUSIC,
                // Request permanent focus.
                AudioManager.AUDIOFOCUS_GAIN);
        if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            // TODO: take appropriate action
        } else if (result == AudioManager.AUDIOFOCUS_REQUEST_FAILED) {
            // TODO: take appropriate error action
        }
    }

    public void pause() {
        if (mMediaPlayer != null && mMediaPlayer.isPlaying()) {
            mMediaPlayer.pause();
            drawPlayPause();
        }
    }

    public void exit() {
        synchronized (canExitLock) {
            Logger.i(ScumTubeApplication.TAG, "Stopping player.");
            if (requestMp3 != null) {
                requestMp3.interrupt();
                requestMp3 = null;
            }
            if (mMediaPlayer != null && mMediaPlayer.isPlaying())
                mMediaPlayer.stop();
            mMediaPlayer.reset();
            final NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            nm.cancel(PLAYERSERVICE_NOTIFICATION_ID);
            //stopSelf();
        }
    }

    public void loop() {
        mMediaPlayer.setLooping(!mMediaPlayer.isLooping());
        drawLoop();
        saveIsLooping();
    }

    public void playPause() {
        if (mMediaPlayer.isPlaying()) {
            pause();
        } else {
            play();
        }
    }

    public void play() {
        if (!mMediaPlayer.isPlaying()) {
            mMediaPlayer.start();
            drawPlayPause();
        }
    }

    public void download() {
        final Intent it = new Intent(Intent.ACTION_CLOSE_SYSTEM_DIALOGS);
        getApplicationContext().sendBroadcast(it);
        final Intent downloadIntent = new Intent(PlayerService.this, DownloadService.class);
        downloadIntent.putExtra("ytUrl", sYtUrl);
        startService(downloadIntent);
    }

    public void createLoadingNotification() {
        synchronized (this) {
            Intent intent = new Intent(ACTION_EXITLOADING, null, PlayerService.this, PlayerService.class);
            PendingIntent exitPendingIntent = PendingIntent
                    .getService(PlayerService.this, 0, intent,
                            PendingIntent.FLAG_UPDATE_CURRENT);

            mSmallLoadingNotificationView
                    .setOnClickPendingIntent(R.id.notification_loading_imageview_exit,
                            exitPendingIntent);

            NotificationCompat.Builder builder = new NotificationCompat.Builder(
                    PlayerService.this)
                    .setSmallIcon(R.drawable.ic_loading).setContentTitle(ScumTubeApplication.APP_NAME)
                    .setOngoing(true).setPriority(NotificationCompat.PRIORITY_MAX)
                    .setContent(mSmallLoadingNotificationView);

            mNotification = builder.build();

            final NotificationManager mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            mNotificationManager.notify(PLAYERSERVICE_NOTIFICATION_ID, mNotification);
            //startForeground(PLAYERSERVICE_NOTIFICATION_ID, mNotification);
        }
    }

    public void drawCover() {
        if (!requestMp3.isInterrupted()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                mLargeNotificationView
                        .setImageViewBitmap(R.id.notification_large_imageview_cover,
                                sCover);
            }
            updateNotification();
        }
    }

    public void drawPlayPause() {
        if (mMediaPlayer.isPlaying()) {
            mSmallNotificationView
                    .setImageViewResource(R.id.notification_small_imageview_playpause,
                            R.drawable.ic_player_pause_light);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                mLargeNotificationView
                        .setImageViewResource(R.id.notification_large_imageview_playpause,
                                R.drawable.ic_player_pause_light);
            }
        } else {
            mSmallNotificationView
                    .setImageViewResource(R.id.notification_small_imageview_playpause,
                            R.drawable.ic_player_play_light);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                mLargeNotificationView
                        .setImageViewResource(R.id.notification_large_imageview_playpause,
                                R.drawable.ic_player_play_light);
            }
        }
        updateNotification();
    }

    public void drawLoop() {
        if (mMediaPlayer.isLooping()) {
            mSmallNotificationView
                    .setImageViewResource(R.id.notification_small_imageview_loop,
                            R.drawable.ic_player_loop_on_light);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                mLargeNotificationView
                        .setImageViewResource(R.id.notification_large_imageview_loop,
                                R.drawable.ic_player_loop_on_light);
            }
        } else {
            mSmallNotificationView
                    .setImageViewResource(R.id.notification_small_imageview_loop,
                            R.drawable.ic_player_loop_off_light);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                mLargeNotificationView
                        .setImageViewResource(R.id.notification_large_imageview_loop,
                                R.drawable.ic_player_loop_off_light);
            }
        }
        updateNotification();
    }

    @Override
    public void onDestroy() {
        Logger.w(ScumTubeApplication.TAG, "Player Service being destroyed.");
        this.exit();
    }

    public void updateNotification() {
        synchronized (this) {
            mNotification.contentView = mSmallNotificationView;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                mNotification.bigContentView = mLargeNotificationView;
            }
            final NotificationManager mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            mNotificationManager.notify(PLAYERSERVICE_NOTIFICATION_ID, mNotification);
        }
    }

    public void createNotification() {
        synchronized (this) {
            Intent intent = new Intent(ACTION_PLAYPAUSE, null, PlayerService.this,
                    PlayerService.class);
            PendingIntent playPausePendingIntent = PendingIntent
                    .getService(PlayerService.this, 0, intent,
                            PendingIntent.FLAG_UPDATE_CURRENT);
            intent = new Intent(ACTION_LOOP, null, PlayerService.this,
                    PlayerService.class);
            PendingIntent loopPendingIntent = PendingIntent
                    .getService(PlayerService.this, 0, intent,
                            PendingIntent.FLAG_UPDATE_CURRENT);
            intent = new Intent(ACTION_EXIT, null, PlayerService.this, PlayerService.class);
            PendingIntent exitPendingIntent = PendingIntent
                    .getService(PlayerService.this, 0, intent,
                            PendingIntent.FLAG_UPDATE_CURRENT);
            intent = new Intent(ACTION_DOWNLOAD, null, PlayerService.this, PlayerService.class);
            PendingIntent downloadPendingIntent = PendingIntent
                    .getService(PlayerService.this, 0, intent,
                            PendingIntent.FLAG_UPDATE_CURRENT);
            intent = new Intent(PlayerService.this, HistoryActivity.class);
            PendingIntent historyPendingIntent = PendingIntent.getActivity(PlayerService.this, 0, intent,
                    PendingIntent.FLAG_UPDATE_CURRENT);

            mSmallNotificationView
                    .setOnClickPendingIntent(R.id.notification_small_imageview_playpause,
                            playPausePendingIntent);
            mSmallNotificationView
                    .setOnClickPendingIntent(R.id.notification_small_imageview_loop,
                            loopPendingIntent);
            mSmallNotificationView
                    .setOnClickPendingIntent(R.id.notification_small_imageview_exit,
                            exitPendingIntent);
            mSmallNotificationView
                    .setOnClickPendingIntent(R.id.notification_small_imageview_download,
                            downloadPendingIntent);
            mSmallNotificationView
                    .setOnClickPendingIntent(R.id.notification_small_wrapper,
                            historyPendingIntent);

            mSmallNotificationView
                    .setTextViewText(R.id.notification_small_textview, ScumTubeApplication.APP_NAME);
            mSmallNotificationView.setTextViewText(R.id.notification_small_textview2,
                    sStreamTitle);

            NotificationCompat.Builder builder = new NotificationCompat.Builder(
                    PlayerService.this)
                    .setSmallIcon(R.drawable.tray).setContentTitle(ScumTubeApplication.APP_NAME)
                    .setContentText(sStreamTitle).setOngoing(true).setPriority(
                            NotificationCompat.PRIORITY_MAX).setContent(mSmallNotificationView);

            mNotification = builder.build();

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                mLargeNotificationView
                        .setOnClickPendingIntent(R.id.notification_large_imageview_playpause,
                                playPausePendingIntent);
                mLargeNotificationView
                        .setOnClickPendingIntent(R.id.notification_large_imageview_loop,
                                loopPendingIntent);
                mLargeNotificationView
                        .setOnClickPendingIntent(R.id.notification_large_imageview_exit,
                                exitPendingIntent);
                mLargeNotificationView
                        .setOnClickPendingIntent(R.id.notification_large_imageview_download,
                                downloadPendingIntent);
                mLargeNotificationView
                        .setOnClickPendingIntent(R.id.notification_large_wrapper,
                                historyPendingIntent);

                mLargeNotificationView.setTextViewText(R.id.notification_large_textview,
                        ScumTubeApplication.APP_NAME);
                mLargeNotificationView
                        .setTextViewText(R.id.notification_large_textview2, sStreamTitle);
            }
            drawPlayPause();
            drawLoop();
            updateNotification();
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        // TODO: Return the communication channel to the service.
        throw new UnsupportedOperationException("Not yet implemented");
    }

    public void updateMusicList() {
        if (!requestMp3.isInterrupted()) {
            MusicList.addFirst(new Music(sStreamTitle, sCover, sYtUrl));
            notifyHistoryActivity();
            MusicList.saveMusicList(getSharedPreferences(ScumTubeApplication.PREFS_NAME, Context.MODE_PRIVATE));
        }
    }

    public void notifyHistoryActivity() {
        if (HistoryActivity.hasView) {
            Intent intent = new Intent(this, HistoryActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            intent.putExtra(Intent.EXTRA_TEXT, EXTRA_DATASETCHANGED);
            startActivity(intent);
        }
    }


    public void saveIsLooping() {
        SharedPreferences preferences = getSharedPreferences(ScumTubeApplication.PREFS_NAME, 0);
        SharedPreferences.Editor editor = preferences.edit();
        editor.putBoolean(ScumTubeApplication.PREFS_ISLOOPING, mMediaPlayer.isLooping());
        editor.commit();
    }

    public void loadLoopPreferencesFromStorage() {
        SharedPreferences preferences = getSharedPreferences(ScumTubeApplication.PREFS_NAME, 0);
        mMediaPlayer.setLooping(preferences.getBoolean(ScumTubeApplication.PREFS_ISLOOPING, false));
    }

    private class AudioManagerListener implements AudioManager.OnAudioFocusChangeListener {

        @Override
        public void onAudioFocusChange(int focusChange) {
            switch (focusChange) {
                case AudioManager.AUDIOFOCUS_GAIN:
                    Logger.i(ScumTubeApplication.TAG, "AUDIOFOCUS_GAIN");
                    // Set volume level to desired levels
                    // returnVolumeToNormal();
                    break;
                case AudioManager.AUDIOFOCUS_GAIN_TRANSIENT:
                    Logger.i(ScumTubeApplication.TAG, "AUDIOFOCUS_GAIN_TRANSIENT");
                    // Set volume level to desired levels
                    // returnVolumeToNormal();
                    play();
                    break;
                case AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK:
                    Logger.i(ScumTubeApplication.TAG, "AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK");
                    // Set volume level to desired levels
                    // returnVolumeToNormal();
                    play();
                    break;
                case AudioManager.AUDIOFOCUS_LOSS:
                    Logger.e(ScumTubeApplication.TAG, "AUDIOFOCUS_LOSS");
                    // Lower the volume
                    pause();
                    break;
                case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
                    Logger.e(ScumTubeApplication.TAG, "AUDIOFOCUS_LOSS_TRANSIENT");
                    // Lower the volume
                    pause();
                    break;
                case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK:
                    Logger.e(ScumTubeApplication.TAG, "AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK");
                    // Lower the volume
                    pause();
                    break;
            }
        }
    }

    private final class RequestMp3 extends Thread {

        private final RequestMp3Task requestMp3Task;
        private DownloadImageTask downloadImageTask;

        public RequestMp3(String videoId) {
            requestMp3Task = new RequestMp3Task(videoId);
        }

        @Override
        public void run() {
            requestMp3Task.start();
            try {
                requestMp3Task.waitUntilFinished();
            } catch (InterruptedException e) {
                requestMp3Task.interrupt();
                return;
            }
            if (requestMp3Task.needsUpdate()) {
                showToast(requestMp3Task.getMessage());
                Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("http://www.scumtube.com"));
                browserIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(browserIntent);
                PlayerService.this.exit();
                return;
            } else if (requestMp3Task.hadSuccess()) {
                sStreamTitle = requestMp3Task.getTitle();
                sStreamCoverUrl = requestMp3Task.getCoverUrl();
                sStreamMp3Url = requestMp3Task.getMp3Url();
                if (this.isInterrupted()) {
                    return;
                }
                synchronized (canExitLock) {
                    try {
                        Logger.i(ScumTubeApplication.TAG, "Drawing notification player...");
                        PlayerService.this.start();
                        Logger.i(ScumTubeApplication.TAG, "Finished drawing notification player.");
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                            downloadImageTask = new DownloadImageTask(sStreamCoverUrl);
                            if (this.isInterrupted()) {
                                return;
                            }
                            downloadImageTask.start();
                        } else {
                            updateMusicList();
                        }
                    } catch (Exception e) {
                        Logger.i(ScumTubeApplication.TAG, e.getClass().getName(), e);
                        System.exit(2);
                    }
                }
                return;
            } else {
                showToast(requestMp3Task.getMessage());
                try {
                    Thread.sleep(5000);
                } catch (InterruptedException e1) {
                }
                PlayerService.this.exit();
                return;
            }
        }
    }

    private final class DownloadImageTask extends Thread {

        private final String imageUrl;

        public DownloadImageTask(String imageUrl) {
            this.imageUrl = imageUrl;
        }

        @Override
        public void run() {
            Logger.i(ScumTubeApplication.TAG, "Loading cover image...");
            try {
                Bitmap image = null;
                try {
                    InputStream in = new java.net.URL(imageUrl).openStream();
                    image = BitmapFactory.decodeStream(in);
                } catch (Exception e) {
                    Logger.e("Error", e.getMessage());
                    e.printStackTrace();
                }
                sCover = image;

                drawCover();
                updateMusicList();
            } catch (Exception e) {
                if (e.getMessage() != null)
                    Logger.i(ScumTubeApplication.TAG, e.getMessage());
            }
            Logger.i(ScumTubeApplication.TAG, "Finished loading cover image.");
        }
    }

    private class PhoneCallListener extends PhoneStateListener {

        private long mStartCallTime = 0L;

        @Override
        public void onCallStateChanged(int state, String incomingNumber) {
            switch (state) {
                case TelephonyManager.CALL_STATE_RINGING:
                    if (mMediaPlayer.isPlaying()) {
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
}
