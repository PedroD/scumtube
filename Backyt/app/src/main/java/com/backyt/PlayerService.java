package com.backyt;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.IBinder;
import android.support.v4.app.NotificationCompat;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.widget.RemoteViews;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.concurrent.ExecutionException;


public class PlayerService extends Service {

    public static final String APP_NAME = "Backyt";
    public static final String TAG = "BackytLOG";

    public static final String ACTION_PLAYPAUSE = "com.backyt.ACTION_PLAYPAUSE";
    public static final String ACTION_PLAY = "com.backyt.ACTION_PLAY";
    public static final String ACTION_PAUSE = "com.backyt.ACTION_PAUSE";
    public static final String ACTION_EXIT = "com.backyt.ACTION_EXIT";
    public static final String ACTION_LOOP = "com.backyt.ACTION_LOOP";

    private static final int PLAYERSERVICE_NOTIFICATION_ID = 1;
    private static boolean isVolumeHalved = false;
    private RemoteViews mSmallNotificationView;
    private boolean mShowingNotification;
    private Notification mNotification;
    private RemoteViews mLargeNotificationView;
    private RemoteViews mSmallLoadingNotificationView;
    private MediaPlayer mMediaPlayer = new MediaPlayer();
    private PhoneCallListener mPhoneCallListener = new PhoneCallListener();
    private String mVideoTitle;
    private String streamUrl;

    public PlayerService() {
    }

    @Override
    public void onCreate() {
        super.onCreate();

        // Initialize PhoneCallListener
        TelephonyManager telephonyManager =
                (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
        telephonyManager.listen(mPhoneCallListener, PhoneStateListener.LISTEN_CALL_STATE);

        mMediaPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
            @Override
            public void onCompletion(MediaPlayer mp) {
                updateNotification();
            }
        });
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent.getExtras() != null) {
            createLoadingNotification();
            String ytUrl = intent.getExtras().getString("ytUrl");
            try {
                streamUrl = new RequestMp3().execute(parseVideoId(ytUrl)).get();
            } catch (InterruptedException e) {
                e.printStackTrace();
            } catch (ExecutionException e) {
                e.printStackTrace();
            }
        }
        if (intent != null && intent.getAction() != null) {
            if (intent.getAction().equals(ACTION_PLAYPAUSE)) {
                playPause();
            } else if (intent.getAction().equals(ACTION_PLAY)) {
                start();
            } else if (intent.getAction().equals(ACTION_LOOP)) {
                loop();
            } else if (intent.getAction().equals(ACTION_EXIT)) {
                exit();
            }
        }
        return START_STICKY;
    }

    private String parseVideoId(String url) {
        String[] splittedUrl = url.split("/");
        return splittedUrl[3]; //splittedUrl[3] is the id of the video
    }

    public void start() {
        try {
            mMediaPlayer.reset();
            mMediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
            mMediaPlayer.setDataSource(this, Uri.parse(streamUrl)); //TODO URL
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

        /*
         * Detect when videos start playing in the background to lower or stop the music.
         */
        final AudioManager am = (AudioManager) getApplicationContext()
                .getSystemService(Context.AUDIO_SERVICE);
        // Request audio focus for play back
        final int result = am.requestAudioFocus(new AudioManagerListener(),
                // Use the music stream.
                AudioManager.STREAM_MUSIC,
                // Request permanent focus.
                AudioManager.AUDIOFOCUS_GAIN);
        if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            // take appropriate action
        } else if (result == AudioManager.AUDIOFOCUS_REQUEST_FAILED) {
            // take appropriate error action
        }
    }

    private class AudioManagerListener implements AudioManager.OnAudioFocusChangeListener {

        @Override
        public void onAudioFocusChange(int focusChange) {
            switch (focusChange) {
                case AudioManager.AUDIOFOCUS_GAIN:
                    Log.i(TAG, "AUDIOFOCUS_GAIN");
                    // Set volume level to desired levels
                    returnVolumeToNormal();
                    break;
                case AudioManager.AUDIOFOCUS_GAIN_TRANSIENT:
                    Log.i(TAG, "AUDIOFOCUS_GAIN_TRANSIENT");
                    // Set volume level to desired levels
                    returnVolumeToNormal();
                    break;
                case AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK:
                    Log.i(TAG, "AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK");
                    // Set volume level to desired levels
                    returnVolumeToNormal();
                    break;
                case AudioManager.AUDIOFOCUS_LOSS:
                    Log.e(TAG, "AUDIOFOCUS_LOSS");
                    // Lower the volume
                    pause();
                    break;
                case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
                    Log.e(TAG, "AUDIOFOCUS_LOSS_TRANSIENT");
                    // Lower the volume
                    halveVolume();
                    break;
                case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK:
                    Log.e(TAG, "AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK");
                    // Lower the volume
                    halveVolume();
                    break;
            }
        }
    }

    public void pause() {
        mMediaPlayer.pause();
        updateNotification();
    }

    public void exit() {
        mMediaPlayer.stop();
        stopSelf();
        android.os.Process.killProcess(android.os.Process.myPid());
    }

    public void loop() {
        mMediaPlayer.setLooping(!mMediaPlayer.isLooping());
        updateNotification();
    }

    public void playPause() {
        if (mMediaPlayer.isPlaying()) {
            pause();
        } else {
            mMediaPlayer.start();
        }
        updateNotification();
    }

    public void play() {
        if (!mMediaPlayer.isPlaying()) {
            mMediaPlayer.start();
            updateNotification();
        }
    }

    public void halveVolume() {
        if (!isVolumeHalved) {
            mMediaPlayer.setVolume(0.1f, 0.1f);
            isVolumeHalved = true;
        }
    }

    public void returnVolumeToNormal() {
        if (isVolumeHalved) {
            mMediaPlayer.setVolume(1f, 1f);
            isVolumeHalved = false;
        }
    }

    public void createLoadingNotification() {
        mSmallLoadingNotificationView = new RemoteViews(getPackageName(), R.layout.notification_loading_small);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(
                PlayerService.this)
                .setSmallIcon(R.drawable.ic_notification).setContentTitle(APP_NAME)
                .setOngoing(true).setPriority(NotificationCompat.PRIORITY_MAX)
                .setContent(mSmallLoadingNotificationView);

        mNotification = builder.build();
        startForeground(PLAYERSERVICE_NOTIFICATION_ID, mNotification);
    }

    public void updateNotification() {
        if (mShowingNotification) {

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
            if (mMediaPlayer.isPlaying()) {
                mSmallNotificationView
                        .setImageViewResource(R.id.notification_small_imageview_playpause,
                                R.drawable.ic_player_pause_light);
            } else {
                mSmallNotificationView
                        .setImageViewResource(R.id.notification_small_imageview_playpause,
                                R.drawable.ic_player_play_light);
            }
            if (mMediaPlayer.isLooping()) {
                mSmallNotificationView
                        .setImageViewResource(R.id.notification_small_imageview_loop,
                                R.drawable.ic_player_loop_on_light);
            } else {
                mSmallNotificationView
                        .setImageViewResource(R.id.notification_small_imageview_loop,
                                R.drawable.ic_player_loop_off_light);
            }
            mSmallNotificationView
                    .setOnClickPendingIntent(R.id.notification_small_imageview_playpause,
                            playPausePendingIntent);
            mSmallNotificationView
                    .setOnClickPendingIntent(R.id.notification_small_imageview_loop,
                            loopPendingIntent);
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
                if (mMediaPlayer.isPlaying()) {
                    mLargeNotificationView
                            .setImageViewResource(R.id.notification_large_imageview_playpause,
                                    R.drawable.ic_player_pause_light);
                } else {
                    mLargeNotificationView
                            .setImageViewResource(R.id.notification_large_imageview_playpause,
                                    R.drawable.ic_player_play_light);
                }
                if (mMediaPlayer.isLooping()) {
                    mLargeNotificationView
                            .setImageViewResource(R.id.notification_large_imageview_loop,
                                    R.drawable.ic_player_loop_on_light);
                } else {
                    mLargeNotificationView
                            .setImageViewResource(R.id.notification_large_imageview_loop,
                                    R.drawable.ic_player_loop_off_light);
                }
                mLargeNotificationView
                        .setOnClickPendingIntent(R.id.notification_large_imageview_playpause,
                                playPausePendingIntent);
                mLargeNotificationView
                        .setOnClickPendingIntent(R.id.notification_large_imageview_loop,
                                loopPendingIntent);
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

    class RequestMp3 extends AsyncTask<String, Void, String> {

        @Override
        protected String doInBackground(String... videoId) {
            String requestUrl = "http://wavedomotics.com:9194/video_id/" + videoId[0];
            HttpClient httpClient = new DefaultHttpClient();
            HttpGet httpGet = new HttpGet(requestUrl);
            Log.i(TAG, "Requesting music " + requestUrl);
            try {
                JSONObject jsonObject;
                while (true) {
                    HttpResponse httpResponse = httpClient.execute(httpGet);
                    HttpEntity httpEntity = httpResponse.getEntity();
                    if (httpEntity != null) {
                        InputStream inputStream = httpEntity.getContent();

                        BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream));
                        StringBuilder stringBuilder = new StringBuilder();

                        String line = bufferedReader.readLine();
                        while (line != null) {
                            stringBuilder.append(line + " \n");
                            line = bufferedReader.readLine();
                        }
                        bufferedReader.close();

                        jsonObject = new JSONObject(stringBuilder.toString());
                        if (jsonObject.has("ready")) {
                            break;
                        }
                        Log.i(TAG, jsonObject.getString("scheduled"));
                        Thread.sleep(5000);
                    }
                }
                Log.i(TAG, jsonObject.getString("url"));
                return jsonObject.getString("url");

            } catch (Exception e) {
                e.printStackTrace();
            }
            return "";
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
