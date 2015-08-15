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

import static com.scumtube.ScumTubeApplication.mLargeNotificationView;
import static com.scumtube.ScumTubeApplication.mSmallLoadingNotificationView;
import static com.scumtube.ScumTubeApplication.mSmallNotificationView;

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
    private static String sStreamMp3Url;
    private static String sStreamCoverUrl;
    private static String sStreamTitle;
    private static Bitmap sCover;
    private static String sYtVideoId;
    private static String sYtUrl;
    private static Object isServiceClosedBooleanLock = new Object();
    private static final MediaPlayer mMediaPlayer = new MediaPlayer();
    private final PhoneCallListener mPhoneCallListener = new PhoneCallListener();
    private final AudioManagerListener mAudioFocusListener = new AudioManagerListener();
    private static Notification mNotification;
    private Thread downloadTask = null;
    private volatile boolean isServiceClosed = true;

    public PlayerService() {
    }

    public static String encodeBitmapTobase64(Bitmap image) {
        Bitmap imagex = image;
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        imagex.compress(Bitmap.CompressFormat.PNG, 100, baos);
        byte[] b = baos.toByteArray();
        String imageEncoded = Base64.encodeToString(b, Base64.DEFAULT);
        return imageEncoded;
    }

    @Override
    public void onCreate() {
        Log.i(ScumTubeApplication.TAG, "On create invoked.");
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
        Log.i(ScumTubeApplication.TAG, "On start command invoked: " + intent);

        synchronized (isServiceClosedBooleanLock) {
            isServiceClosed = false;
        }

        if (downloadTask != null) {
            Log.i(ScumTubeApplication.TAG, "Killing previous download task.");
            downloadTask.interrupt();
            downloadTask = null;
        }

        if (intent.getExtras() != null) {
            if (mMediaPlayer.isPlaying()) {
                mMediaPlayer.stop();
            }
            createLoadingNotification();
            sYtUrl = intent.getExtras().getString("ytUrl");
            sYtVideoId = parseVideoId(sYtUrl);
            downloadTask = new RequestMp3Task(sYtVideoId, new Runnable() {
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
                        Log.i(ScumTubeApplication.TAG, e.getClass().getName(), e);
                        PlayerService.this.exit();
                    }
                }
            });
            downloadTask.start();
        } else if (intent.getAction().equals(ACTION_EXITLOADING)) {
            exit();
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
        return START_NOT_STICKY;
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
        Log.i(ScumTubeApplication.TAG, "Exiting service.");
        synchronized (isServiceClosedBooleanLock) {
            isServiceClosed = true;
        }
        if (mMediaPlayer != null && mMediaPlayer.isPlaying())
            mMediaPlayer.stop();
        if (downloadTask != null) {
            downloadTask.interrupt();
            downloadTask = null;
        }
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
        downloadTask = new RequestMp3Task(sYtVideoId, new Runnable() {
            @Override
            public void run() {
                final Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(sStreamMp3Url));
                browserIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(browserIntent);
            }
        });
        downloadTask.start();
    }

    public void createLoadingNotification() {
        synchronized (this) {
            Intent intent = new Intent(ACTION_EXITLOADING, null, PlayerService.this, PlayerService.class);
            PendingIntent exitPendingIntent = PendingIntent
                    .getService(PlayerService.this, 0, intent,
                            PendingIntent.FLAG_UPDATE_CURRENT);

            mSmallLoadingNotificationView
                    .setOnClickPendingIntent(R.id.notification_loading_small_imageview_exit,
                            exitPendingIntent);

            NotificationCompat.Builder builder = new NotificationCompat.Builder(
                    PlayerService.this)
                    .setSmallIcon(R.drawable.ic_loading).setContentTitle(ScumTubeApplication.APP_NAME)
                    .setOngoing(true).setPriority(NotificationCompat.PRIORITY_MAX)
                    .setContent(mSmallLoadingNotificationView);

            mNotification = builder.build();


            startForeground(PLAYERSERVICE_NOTIFICATION_ID, mNotification);
        }
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
        synchronized (this) {
            mNotification.contentView = mSmallNotificationView;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                mNotification.bigContentView = mLargeNotificationView;
            }
            NotificationManager mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
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

    public void saveIsLooping() {
        SharedPreferences preferences = getSharedPreferences(PREFS_NAME, 0);
        SharedPreferences.Editor editor = preferences.edit();
        editor.putBoolean(PREFS_ISLOOPING, mMediaPlayer.isLooping());
        editor.commit();
    }

    public void loadLoopPreferencesFromStorage() {
        SharedPreferences preferences = getSharedPreferences(PREFS_NAME, 0);
        mMediaPlayer.setLooping(preferences.getBoolean(PREFS_ISLOOPING, false));
    }

    private class AudioManagerListener implements AudioManager.OnAudioFocusChangeListener {

        @Override
        public void onAudioFocusChange(int focusChange) {
            switch (focusChange) {
                case AudioManager.AUDIOFOCUS_GAIN:
                    Log.i(ScumTubeApplication.TAG, "AUDIOFOCUS_GAIN");
                    // Set volume level to desired levels
                    // returnVolumeToNormal();
                    break;
                case AudioManager.AUDIOFOCUS_GAIN_TRANSIENT:
                    Log.i(ScumTubeApplication.TAG, "AUDIOFOCUS_GAIN_TRANSIENT");
                    // Set volume level to desired levels
                    // returnVolumeToNormal();
                    play();
                    break;
                case AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK:
                    Log.i(ScumTubeApplication.TAG, "AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK");
                    // Set volume level to desired levels
                    // returnVolumeToNormal();
                    play();
                    break;
                case AudioManager.AUDIOFOCUS_LOSS:
                    Log.e(ScumTubeApplication.TAG, "AUDIOFOCUS_LOSS");
                    // Lower the volume
                    pause();
                    break;
                case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
                    Log.e(ScumTubeApplication.TAG, "AUDIOFOCUS_LOSS_TRANSIENT");
                    // Lower the volume
                    pause();
                    break;
                case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK:
                    Log.e(ScumTubeApplication.TAG, "AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK");
                    // Lower the volume
                    pause();
                    break;
            }
        }
    }

    final class RequestMp3Task extends Thread {

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
            Log.i(ScumTubeApplication.TAG, "Requesting music " + requestUrl);
            try {
                JSONObject jsonObject;
                while (!this.isInterrupted()) {
                    HttpResponse httpResponse = httpClient.execute(httpGet);
                    HttpEntity httpEntity = httpResponse.getEntity();
                    if (httpEntity != null) {
                        InputStream inputStream = httpEntity.getContent();

                        BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream));
                        StringBuilder stringBuilder = new StringBuilder();

                        String line = bufferedReader.readLine();
                        while (line != null && !this.isInterrupted()) {
                            stringBuilder.append(line);
                            stringBuilder.append(" \n");
                            line = bufferedReader.readLine();
                        }
                        bufferedReader.close();

                        if (this.isInterrupted()) {
                            Log.w(ScumTubeApplication.TAG, "Download task interrupted");
                            return;
                        }

                        jsonObject = new JSONObject(stringBuilder.toString());
                        if (jsonObject.has("ready")) {
                            sStreamMp3Url = jsonObject.getString("url");
                            sStreamCoverUrl = jsonObject.getString("cover");
                            sStreamTitle = jsonObject.getString("title");
                            Log.i(ScumTubeApplication.TAG, sStreamTitle + " :: " + sStreamMp3Url + " :: " + sStreamCoverUrl);
                            onSuccess.run();
                            return;
                        } else if (jsonObject.has("error")) {
                            final String errorMsg = jsonObject.getString("error");
                            throw new Exception(errorMsg);
                        }
                        Log.i(ScumTubeApplication.TAG, jsonObject.getString("scheduled"));
                        Thread.sleep(2000);
                    }
                }
                if (this.isInterrupted()) {
                    Log.w(ScumTubeApplication.TAG, "Download task interrupted");
                }
            } catch (InterruptedException e) {
                Log.w(ScumTubeApplication.TAG, "Download task interrupted");
                return;
            } catch (Exception e) {
                e.printStackTrace();
                Log.e(ScumTubeApplication.TAG, e.getClass().getName(), e);
                showToast("There was a problem contacting YouTube. Please check your Internet connection.");
                try {
                    Thread.sleep(5000);
                } catch (InterruptedException e1) {
                }
                PlayerService.this.exit();
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
                    Log.i(ScumTubeApplication.TAG, e.getMessage());
            }
            return null;
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
