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
import android.os.AsyncTask;
import android.os.Build;
import android.os.IBinder;
import android.support.v4.app.NotificationCompat;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.util.Base64;
import android.util.Log;
import android.widget.RemoteViews;
import android.widget.Toast;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;


public class PlayerService extends AbstractService {

    public static final String PREFS_NAME = "scumtube_preferences";
    public static final String PREFS_ISLOOPING = "isLooping";
    public static final String PREFS_MUSICLIST = "MusicList";


    public static final String ACTION_PLAYPAUSE = "com.scumtube.ACTION_PLAYPAUSE";
    public static final String ACTION_PLAY = "com.scumtube.ACTION_PLAY";
    public static final String ACTION_PAUSE = "com.scumtube.ACTION_PAUSE";
    public static final String ACTION_EXIT = "com.scumtube.ACTION_EXIT";
    public static final String ACTION_EXITLOADING = "com.scumtube.ACTION_EXITLOADING";
    public static final String ACTION_LOOP = "com.scumtube.ACTION_LOOP";
    public static final String ACTION_DOWNLOAD = "com.scumtube.ACTION_DOWNLOAD";


    public static final String EXTRA_DATASETCHANGED = "Data Set Changed";

    private static final int PLAYERSERVICE_NOTIFICATION_ID = 1;
    private static boolean sIsVolumeHalved = false;
    private static String sStreamMp3Url;
    private static String sStreamCoverUrl;
    private static String sStreamTitle;
    private static Bitmap sCover;
    private static String sYtVideoId;
    private static String sYtUrl;
    private RemoteViews mSmallNotificationView;
    private boolean mShowingNotification;
    private Notification mNotification;
    private RemoteViews mLargeNotificationView;
    private RemoteViews mSmallLoadingNotificationView;
    private MediaPlayer mMediaPlayer = new MediaPlayer();
    private PhoneCallListener mPhoneCallListener = new PhoneCallListener();
    private final AudioManagerListener mAudioFocusListener = new AudioManagerListener();

    public PlayerService() {
    }

    @Override
    public void onCreate() {
        Log.i(ScumTube.TAG, "On create invoked.");
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
        Log.i(ScumTube.TAG, "On start command invoked: " + intent);

        if (intent.getExtras() != null) {
            if (mMediaPlayer.isPlaying()) {
                mMediaPlayer.stop();
            }
            createLoadingNotification();
            sYtUrl = intent.getExtras().getString("ytUrl");
            sYtVideoId = parseVideoId(sYtUrl);
            final Thread downloadTask = new RequestMp3Task(sYtVideoId, new Runnable() {
                @Override
                public void run() {
                    try {
                        PlayerService.this.start();
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                            new DownloadImageTask().execute(sStreamCoverUrl);
                        } else {
                            updateMusicList();
                        }
                    } catch (Exception e) {
                        if (e.getMessage() != null)
                            Log.i(ScumTube.TAG, e.getMessage());
                    }
                }
            });
            downloadTask.start();
        } else if (intent.getAction().equals(ACTION_EXITLOADING)) {
            android.os.Process.killProcess(android.os.Process.myPid());
            return START_NOT_STICKY;
        } else if (intent.getAction().equals(ACTION_PLAYPAUSE)) {
            playPause();
        } else if (intent.getAction().equals(ACTION_PLAY)) {
            start();
        } else if (intent.getAction().equals(ACTION_LOOP)) {
            loop();
        } else if (intent.getAction().equals(ACTION_EXIT)) {
            exit();
            return START_NOT_STICKY;
        } else if (intent.getAction().equals(ACTION_DOWNLOAD)) {
            download();
        } else {
            Toast.makeText(getApplicationContext(), "An error occurred while accessing the video!", Toast.LENGTH_LONG).show();
            return START_NOT_STICKY;
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

        loadIsLooping();

        mShowingNotification = true;
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
        mMediaPlayer.pause();
        drawPlayPause();
    }

    public void exit() {
        mMediaPlayer.stop();
        stopSelf();
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
        showToast("The download will start shortly.");
        final Thread downloadTask = new RequestMp3Task(sYtVideoId, new Runnable() {
            @Override
            public void run() {
                final Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(sStreamMp3Url));
                browserIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(browserIntent);
            }
        });
        downloadTask.start();
    }

    public void halveVolume() {
        if (!sIsVolumeHalved) {
            mMediaPlayer.setVolume(0.1f, 0.1f);
            sIsVolumeHalved = true;
        }
    }

    public void returnVolumeToNormal() {
        if (sIsVolumeHalved) {
            mMediaPlayer.setVolume(1f, 1f);
            sIsVolumeHalved = false;
        }
    }

    public void createLoadingNotification() {
        mSmallLoadingNotificationView = new RemoteViews(getPackageName(), R.layout.notification_loading_small);

        Intent intent = new Intent(ACTION_EXITLOADING, null, PlayerService.this, PlayerService.class);
        PendingIntent exitPendingIntent = PendingIntent
                .getService(PlayerService.this, 0, intent,
                        PendingIntent.FLAG_UPDATE_CURRENT);

        mSmallLoadingNotificationView
                .setOnClickPendingIntent(R.id.notification_loading_small_imageview_exit,
                        exitPendingIntent);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(
                PlayerService.this)
                .setSmallIcon(R.drawable.ic_loading).setContentTitle(ScumTube.APP_NAME)
                .setOngoing(true).setPriority(NotificationCompat.PRIORITY_MAX)
                .setContent(mSmallLoadingNotificationView);

        mNotification = builder.build();


        startForeground(PLAYERSERVICE_NOTIFICATION_ID, mNotification);
    }

    public void drawCover() {
        mSmallNotificationView
                .setImageViewBitmap(R.id.notification_small_imageview_albumart,
                        sCover);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            mLargeNotificationView
                    .setImageViewBitmap(R.id.notification_large_imageview_albumart,
                            sCover);
        }
        updateNotification();
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

    public void updateNotification() {
        mNotification.contentView = mSmallNotificationView;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            mNotification.bigContentView = mLargeNotificationView;
        }
        NotificationManager mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        mNotificationManager.notify(PLAYERSERVICE_NOTIFICATION_ID, mNotification);
    }

    public void createNotification() {
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
            intent = new Intent(ACTION_DOWNLOAD, null, PlayerService.this, PlayerService.class);
            PendingIntent downloadPendingIntent = PendingIntent
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
                    .setTextViewText(R.id.notification_small_textview, ScumTube.APP_NAME);
            mSmallNotificationView.setTextViewText(R.id.notification_small_textview2,
                    sStreamTitle);

            NotificationCompat.Builder builder = new NotificationCompat.Builder(
                    PlayerService.this)
                    .setSmallIcon(R.drawable.tray).setContentTitle(ScumTube.APP_NAME)
                    .setContentText(sStreamTitle).setOngoing(true).setPriority(
                            NotificationCompat.PRIORITY_MAX).setContent(mSmallNotificationView);

            mNotification = builder.build();

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                mLargeNotificationView = new RemoteViews(getPackageName(),
                        R.layout.notification_large);
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

                mLargeNotificationView.setTextViewText(R.id.notification_large_textview,
                        ScumTube.APP_NAME);
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

    private class AudioManagerListener implements AudioManager.OnAudioFocusChangeListener {

        @Override
        public void onAudioFocusChange(int focusChange) {
            switch (focusChange) {
                case AudioManager.AUDIOFOCUS_GAIN:
                    Log.i(ScumTube.TAG, "AUDIOFOCUS_GAIN");
                    // Set volume level to desired levels
                    // returnVolumeToNormal();
                    break;
                case AudioManager.AUDIOFOCUS_GAIN_TRANSIENT:
                    Log.i(ScumTube.TAG, "AUDIOFOCUS_GAIN_TRANSIENT");
                    // Set volume level to desired levels
                    // returnVolumeToNormal();
                    play();
                    break;
                case AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK:
                    Log.i(ScumTube.TAG, "AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK");
                    // Set volume level to desired levels
                    // returnVolumeToNormal();
                    play();
                    break;
                case AudioManager.AUDIOFOCUS_LOSS:
                    Log.e(ScumTube.TAG, "AUDIOFOCUS_LOSS");
                    // Lower the volume
                    pause();
                    break;
                case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
                    Log.e(ScumTube.TAG, "AUDIOFOCUS_LOSS_TRANSIENT");
                    // Lower the volume
                    pause();
                    break;
                case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK:
                    Log.e(ScumTube.TAG, "AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK");
                    // Lower the volume
                    pause();
                    break;
            }
        }
    }

    class RequestMp3Task extends Thread {

        private final String videoId;
        private final Runnable onSuccess;

        public RequestMp3Task(String videoId, Runnable onSuccess) {
            this.videoId = videoId;
            this.onSuccess = onSuccess;
        }

        @Override
        public void run() {
            String requestUrl = "http://176.111.109.11:9194/video_id/" + videoId;
            HttpClient httpClient = new DefaultHttpClient();
            HttpGet httpGet = new HttpGet(requestUrl);
            Log.i(ScumTube.TAG, "Requesting music " + requestUrl);
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
                            stringBuilder.append(line);
                            stringBuilder.append(" \n");
                            line = bufferedReader.readLine();
                        }
                        bufferedReader.close();

                        jsonObject = new JSONObject(stringBuilder.toString());
                        if (jsonObject.has("ready")) {
                            sStreamMp3Url = jsonObject.getString("url");
                            sStreamCoverUrl = jsonObject.getString("cover");
                            sStreamTitle = jsonObject.getString("title");
                            Log.i(ScumTube.TAG, sStreamTitle + " :: " + sStreamMp3Url + " :: " + sStreamCoverUrl);
                            onSuccess.run();
                            return;
                        } else if (jsonObject.has("error")) {
                            final String errorMsg = jsonObject.getString("error");
                            throw new Exception(errorMsg);
                        }
                        Log.i(ScumTube.TAG, jsonObject.getString("scheduled"));
                        Thread.sleep(2000);
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
                Log.e(ScumTube.TAG, e.getMessage());
                showToast(e.getMessage());
                try {
                    Thread.sleep(5000);
                } catch (InterruptedException e1) {
                }
                exit();
            }
        }
    }

    private final class DownloadImageTask extends AsyncTask<String, Void, Void> {

        @Override
        protected Void doInBackground(String... urls) {
            try {
                String coverUrl = urls[0];
                Bitmap cover = null;
                try {
                    InputStream in = new java.net.URL(coverUrl).openStream();
                    cover = BitmapFactory.decodeStream(in);
                } catch (Exception e) {
                    Log.e("Error", e.getMessage());
                    e.printStackTrace();
                }
                sCover = cover;
                drawCover();
                updateMusicList();
            } catch (Exception e) {
                if (e.getMessage() != null)
                    Log.i(ScumTube.TAG, e.getMessage());
            }
            return null;
        }
    }

    public void updateMusicList() {
        MusicList.addFirst(new Music(sStreamTitle, sCover, sYtUrl));
        notifyHistoryActivity();
        saveMusicList();
    }

    public void notifyHistoryActivity() {
        if (HistoryActivity.hasView) {
            Intent intent = new Intent(this, HistoryActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            intent.putExtra(Intent.EXTRA_TEXT, EXTRA_DATASETCHANGED);
            startActivity(intent);
        }
    }

    public void saveMusicList() {
        SharedPreferences preferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = preferences.edit();
        ArrayList<Music> a = MusicList.getMusicArrayList();
        JSONArray musicJsonArray = new JSONArray();
        for (Music m : a) {
            JSONObject musicJsonObject = new JSONObject();
            try {
                musicJsonObject.put("title", m.getTitle());
                musicJsonObject.put("cover", encodeBitmapTobase64(m.getCover()));
                musicJsonObject.put("ytUrl", m.getYtUrl());
                musicJsonArray.put(musicJsonObject);
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
        editor.putString(PREFS_MUSICLIST, musicJsonArray.toString());
        editor.commit();

    }

    public static String encodeBitmapTobase64(Bitmap image) {
        Bitmap imagex = image;
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        imagex.compress(Bitmap.CompressFormat.PNG, 100, baos);
        byte[] b = baos.toByteArray();
        String imageEncoded = Base64.encodeToString(b, Base64.DEFAULT);
        return imageEncoded;
    }

    public void saveIsLooping() {
        SharedPreferences preferences = getSharedPreferences(PREFS_NAME, 0);
        SharedPreferences.Editor editor = preferences.edit();
        editor.putBoolean(PREFS_ISLOOPING, mMediaPlayer.isLooping());
        editor.commit();
    }

    public void loadIsLooping() {
        SharedPreferences preferences = getSharedPreferences(PREFS_NAME, 0);
        mMediaPlayer.setLooping(preferences.getBoolean(PREFS_ISLOOPING, false));
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
