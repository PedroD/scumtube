package com.soundtape;

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
import android.view.View;
import android.widget.RemoteViews;
import android.widget.Toast;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.concurrent.locks.ReentrantLock;

import static com.soundtape.SoundtapeApplication.TYPE_PLAYLIST;
import static com.soundtape.SoundtapeApplication.mLargeNotificationView;
import static com.soundtape.SoundtapeApplication.mSmallLoadingNotificationView;
import static com.soundtape.SoundtapeApplication.mSmallNotificationView;

public class PlayerService extends AbstractService {

    private static final String ACTION_PLAYPAUSE = "com.soundtape.ACTION_PLAYPAUSE";
    private static final String ACTION_PLAY = "com.soundtape.ACTION_PLAY";
    private static final String ACTION_PAUSE = "com.soundtape.ACTION_PAUSE";
    private static final String ACTION_EXIT = "com.soundtape.ACTION_EXIT";
    private static final String ACTION_EXITLOADING = "com.soundtape.ACTION_EXITLOADING";
    private static final String ACTION_MODE = "com.soundtape.ACTION_MODE";
    private static final String ACTION_DOWNLOAD = "com.soundtape.ACTION_DOWNLOAD";
    private static final String ACTION_PREVIOUS = "com.soundtape.ACTION_PREVIOUS";
    private static final String ACTION_NEXT = "com.soundtape.ACTION_NEXT";

    private static final String MODE_NORMAL = "com.soundtape.MODE_NORMAL";
    private static final String MODE_LOOPONE = "com.soundtape.MODE_LOOPONE";
    private static final String MODE_LOOPALL = "com.soundtape.MODE_LOOPALL";
    private static final String MODE_SHUFFLE = "com.soundtape.MODE_SHUFFLE";

    public static final String EXTRA_DATASETCHANGED = "Data Set Changed";

    private static final int PLAYERSERVICE_NOTIFICATION_ID = 1;
    private String streamMp3Url;
    private String streamCoverUrl;
    private String streamTitle;
    private Bitmap cover;
    private String ytUrl;
    private String type;
    private Playlist playlist;
    private final MediaPlayer mediaPlayer = new MediaPlayer();
    private final PhoneCallListener phoneCallListener = new PhoneCallListener();
    private final AudioManagerListener audioFocusListener = new AudioManagerListener();
    private Notification notification;
    private Thread requestMp3 = null;
    private Thread requestPlaylist = null;
    private Object canExitLock = new ReentrantLock();
    private Object canUpdateMusicList = new ReentrantLock();
    private String mode = MODE_NORMAL;

    public PlayerService() {
    }

    @Override
    public void onCreate() {
        Logger.i(SoundtapeApplication.TAG, "On create invoked.");
        super.onCreate();

        // Initialize PhoneCallListener
        TelephonyManager telephonyManager =
                (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
        telephonyManager.listen(phoneCallListener, PhoneStateListener.LISTEN_CALL_STATE);

        mediaPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
            @Override
            public void onCompletion(MediaPlayer mp) {
                if (mode.equals(MODE_NORMAL) && type.equals(SoundtapeApplication.TYPE_MUSIC)) {
                    drawPlayPause();
                } else if (mode.equals(MODE_NORMAL) && type.equals(SoundtapeApplication.TYPE_PLAYLIST)) {
                    if (!playlist.isLastMusic()) {
                        next();
                    } else {
                        drawPlayPause();
                    }
                } else if (mode.equals(MODE_LOOPALL)) {
                    next();
                } else if (mode.equals(MODE_SHUFFLE)) {
                    next();
                }
            }
        });
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Logger.i(SoundtapeApplication.TAG, "On start command invoked: " + intent);

        synchronized (canExitLock) {
            if (intent.getExtras() != null) {
                if (requestPlaylist != null) {
                    requestPlaylist.interrupt();
                    requestPlaylist = null;
                }
                if (requestMp3 != null) {
                    Logger.i(SoundtapeApplication.TAG, "Killing previous download requestMp3Task.");
                    requestMp3.interrupt();
                    requestMp3 = null;
                }
                if (mediaPlayer.isPlaying()) {
                    mediaPlayer.stop();
                }
                mediaPlayer.reset();
                createLoadingNotification();
                ytUrl = intent.getExtras().getString("ytUrl");
                type = intent.getExtras().getString("type");
                if (type.equals(SoundtapeApplication.TYPE_MUSIC)) {
                    final String ytVideoId = SoundtapeApplication.parseVideoId(ytUrl);
                    requestMp3 = new RequestMp3(ytVideoId);
                    requestMp3.start();
                } else if (type.equals(SoundtapeApplication.TYPE_PLAYLIST)) {
                    final String ytPlaylistId = SoundtapeApplication.parsePlaylistId(ytUrl);
                    requestPlaylist = new RequestPlaylist(ytPlaylistId);
                    requestPlaylist.start();
                }

            } else if (intent.getAction().equals(ACTION_EXITLOADING)) {
                exit();
            } else if (intent.getAction().equals(ACTION_PLAYPAUSE)) {
                playPause();
            } else if (intent.getAction().equals(ACTION_PLAY)) {
                start();
            } else if (intent.getAction().equals(ACTION_MODE)) {
                mode();
            } else if (intent.getAction().equals(ACTION_EXIT)) {
                exit();
            } else if (intent.getAction().equals(ACTION_DOWNLOAD)) {
                download();
            } else if (intent.getAction().equals(ACTION_PREVIOUS)) {
                previous();
            } else if (intent.getAction().equals(ACTION_NEXT)) {
                next();
            } else {
                Toast.makeText(getApplicationContext(), "An error occurred while accessing the video!", Toast.LENGTH_LONG).show();
            }
        }
        return START_NOT_STICKY;
    }

    private class ChangeMusicThread extends Thread {
        private final Runnable runnable;

        public ChangeMusicThread(Runnable runnable) {
            this.runnable = runnable;
        }

        @Override
        public void run() {
            try {
                Thread.sleep(1000);
                if (!this.isInterrupted())
                    runnable.run();
            } catch (Exception e) {
            }
        }
    }

    private ChangeMusicThread changeMusicThread = null;

    public void requestCurrent() {
        if (changeMusicThread != null) {
            changeMusicThread.interrupt();
        }
        changeMusicThread = new ChangeMusicThread(new Runnable() {
            @Override
            public void run() {
                requestMp3 = new RequestMp3(SoundtapeApplication.parseVideoId(playlist.getCurrentMusicId()));
                requestMp3.start();
                new RequestMp3Task(SoundtapeApplication.parseVideoId(playlist.getNextMusicId())).start();
                if (!mode.equals(MODE_SHUFFLE)) {
                    new RequestMp3Task(SoundtapeApplication.parseVideoId(playlist.getPreviousMusicId())).start();
                }
            }
        });
        changeMusicThread.start();
    }

    public void previous() {
        drawLoadingMusic();
        if (requestMp3 != null) {
            Logger.i(SoundtapeApplication.TAG, "Killing previous download requestMp3Task.");
            requestMp3.interrupt();
            requestMp3 = null;
        }
        pause();
        playlist.changeToPreviousMusic();
        requestCurrent();
    }

    public void next() {
        drawLoadingMusic();
        if (requestMp3 != null) {
            Logger.i(SoundtapeApplication.TAG, "Killing previous download requestMp3Task.");
            requestMp3.interrupt();
            requestMp3 = null;
        }
        pause();
        playlist.changeToNextMusic();
        requestCurrent();
    }

    public void start() {
        try {
            mediaPlayer.reset();
            mediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
            mediaPlayer.setDataSource(this, Uri.parse(streamMp3Url));
        } catch (Exception e) {
            e.printStackTrace();
        }
        try {
            mediaPlayer.prepare(); // might take long! (for buffering, etc)
        } catch (IOException e) {
            e.printStackTrace();
        }
        mediaPlayer.start();

        loadMode();

        createNotification();

        /*
         * Detect when videos start playing in the background to lower or stop the music.
         */
        final AudioManager am = (AudioManager) getApplicationContext()
                .getSystemService(Context.AUDIO_SERVICE);
        // Request audio focus for play back
        final int result = am.requestAudioFocus(audioFocusListener,
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
        if (mediaPlayer != null && mediaPlayer.isPlaying()) {
            mediaPlayer.pause();
            drawPlayPause();
        }
    }

    public void exit() {
        synchronized (canExitLock) {
            Logger.i(SoundtapeApplication.TAG, "Stopping player.");
            if (requestPlaylist != null) {
                requestPlaylist.interrupt();
                requestPlaylist = null;
            }
            if (requestMp3 != null) {
                requestMp3.interrupt();
                requestMp3 = null;
            }
            if (mediaPlayer != null && mediaPlayer.isPlaying())
                mediaPlayer.stop();
            mediaPlayer.reset();
            final NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            nm.cancel(PLAYERSERVICE_NOTIFICATION_ID);
            stopSelf();
        }
    }

    public void mode() {
        if (mode.equals(MODE_NORMAL)) {
            if(type.equals(TYPE_PLAYLIST)) {
                playlist.setShuffle(false);
            }
            mediaPlayer.setLooping(true);
            mode = MODE_LOOPONE;
        } else if (mode.equals(MODE_LOOPONE)) {
            mediaPlayer.setLooping(false);
            if (type.equals(SoundtapeApplication.TYPE_PLAYLIST)) {
                mode = MODE_LOOPALL;
            } else if (type.equals(SoundtapeApplication.TYPE_MUSIC)) {
                mode = MODE_NORMAL;
            }
        } else if (mode.equals(MODE_LOOPALL)) {
            mode = MODE_SHUFFLE;
        } else if (mode.equals(MODE_SHUFFLE)) {
            playlist.setShuffle(true);
            mode = MODE_NORMAL;
        }
        drawMode();
        saveMode();
    }

    public void playPause() {
        if (mediaPlayer.isPlaying()) {
            pause();
        } else {
            play();
        }
    }

    public void play() {
        if (!mediaPlayer.isPlaying()) {
            mediaPlayer.start();
            drawPlayPause();
        }
    }

    public void download() {
        final Intent it = new Intent(Intent.ACTION_CLOSE_SYSTEM_DIALOGS);
        getApplicationContext().sendBroadcast(it);
        final Intent downloadIntent = new Intent(PlayerService.this, DownloadService.class);
        if (type.equals(SoundtapeApplication.TYPE_MUSIC)) {
            downloadIntent.putExtra("ytUrl", ytUrl);
        } else if (type.equals(SoundtapeApplication.TYPE_PLAYLIST)) {
            downloadIntent.putExtra("ytUrl", playlist.getCurrentMusicId());
        }
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
                    .setSmallIcon(R.drawable.ic_loading).setContentTitle(SoundtapeApplication.APP_NAME)
                    .setOngoing(true).setPriority(NotificationCompat.PRIORITY_MAX)
                    .setContent(mSmallLoadingNotificationView);

            notification = builder.build();

            final NotificationManager mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            mNotificationManager.notify(PLAYERSERVICE_NOTIFICATION_ID, notification);
            startForeground(PLAYERSERVICE_NOTIFICATION_ID, notification);
        }
    }

    public void drawLoadingMusic() {
        mSmallNotificationView
                .setViewVisibility(R.id.marker_progress, View.VISIBLE);
        mSmallNotificationView
                .setViewVisibility(R.id.notification_small_imageview_playpause, View.GONE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            mLargeNotificationView
                    .setViewVisibility(R.id.notification_large_imageview_cover, View.GONE);
            mLargeNotificationView
                    .setViewVisibility(R.id.marker_progress, View.VISIBLE);
            mLargeNotificationView.setTextViewText(R.id.notification_large_textview2, getResources().getString(R.string.loading));
        }
        updateNotification();
    }

    public void drawCover() {
        if (!requestMp3.isInterrupted()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                mLargeNotificationView
                        .setViewVisibility(R.id.notification_large_imageview_cover, View.VISIBLE);
                mLargeNotificationView
                        .setViewVisibility(R.id.marker_progress, View.GONE);
                mLargeNotificationView
                        .setImageViewBitmap(R.id.notification_large_imageview_cover,
                                cover);
            }
            updateNotification();
        }
    }

    public void drawPlayPause() {
        if (type.equals(SoundtapeApplication.TYPE_PLAYLIST)) {
            mSmallNotificationView
                    .setViewVisibility(R.id.marker_progress, View.GONE);
            mSmallNotificationView
                    .setViewVisibility(R.id.notification_small_imageview_playpause, View.VISIBLE);
        }
        if (mediaPlayer.isPlaying()) {
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

    public void drawMode() {
        if (mode.equals(MODE_NORMAL)) {
            mSmallNotificationView
                    .setImageViewResource(R.id.notification_small_imageview_mode,
                            R.drawable.ic_player_mode_normal);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                mLargeNotificationView
                        .setImageViewResource(R.id.notification_large_imageview_mode,
                                R.drawable.ic_player_mode_normal);
            }
        } else if (mode.equals(MODE_LOOPONE)) {
            mSmallNotificationView
                    .setImageViewResource(R.id.notification_small_imageview_mode,
                            R.drawable.ic_player_mode_loopone);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                mLargeNotificationView
                        .setImageViewResource(R.id.notification_large_imageview_mode,
                                R.drawable.ic_player_mode_loopone);
            }
        } else if (mode.equals(MODE_LOOPALL)) {
            mSmallNotificationView
                    .setImageViewResource(R.id.notification_small_imageview_mode,
                            R.drawable.ic_player_mode_loopall);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                mLargeNotificationView
                        .setImageViewResource(R.id.notification_large_imageview_mode,
                                R.drawable.ic_player_mode_loopall);
            }

        } else if (mode.equals(MODE_SHUFFLE)) {
            mSmallNotificationView
                    .setImageViewResource(R.id.notification_small_imageview_mode,
                            R.drawable.ic_player_mode_shuffle);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                mLargeNotificationView
                        .setImageViewResource(R.id.notification_large_imageview_mode,
                                R.drawable.ic_player_mode_shuffle);
            }
        }
        updateNotification();
    }

    @Override
    public void onDestroy() {
        Logger.w(SoundtapeApplication.TAG, "Player Service being destroyed.");
        this.exit();
    }

    public void updateNotification() {
        synchronized (this) {
            notification.contentView = mSmallNotificationView;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                notification.bigContentView = mLargeNotificationView;
            }
            final NotificationManager mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            mNotificationManager.notify(PLAYERSERVICE_NOTIFICATION_ID, notification);
        }
    }

    public void createNotification() {
        synchronized (this) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                if (type.equals(SoundtapeApplication.TYPE_MUSIC)) {
                    mLargeNotificationView = new RemoteViews(getPackageName(),
                            R.layout.notification_large_music);
                    mSmallNotificationView = new RemoteViews(getPackageName(),
                            R.layout.notification_small_music);
                } else if (type.equals(SoundtapeApplication.TYPE_PLAYLIST)) {
                    mLargeNotificationView = new RemoteViews(getPackageName(),
                            R.layout.notification_large_playlist);
                    mSmallNotificationView = new RemoteViews(getPackageName(),
                            R.layout.notification_small_playlist);
                }
            }

            Intent intent = new Intent(ACTION_PLAYPAUSE, null, PlayerService.this,
                    PlayerService.class);
            PendingIntent playPausePendingIntent = PendingIntent
                    .getService(PlayerService.this, 0, intent,
                            PendingIntent.FLAG_UPDATE_CURRENT);
            intent = new Intent(ACTION_MODE, null, PlayerService.this,
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
                    .setOnClickPendingIntent(R.id.notification_small_imageview_mode,
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

            if (type.equals(SoundtapeApplication.TYPE_PLAYLIST)) {
                intent = new Intent(ACTION_PREVIOUS, null, PlayerService.this,
                        PlayerService.class);
                PendingIntent previousPendingIntent = PendingIntent
                        .getService(PlayerService.this, 0, intent,
                                PendingIntent.FLAG_UPDATE_CURRENT);
                intent = new Intent(ACTION_NEXT, null, PlayerService.this,
                        PlayerService.class);
                PendingIntent nextPendingIntent = PendingIntent
                        .getService(PlayerService.this, 0, intent,
                                PendingIntent.FLAG_UPDATE_CURRENT);
                mSmallNotificationView
                        .setOnClickPendingIntent(R.id.notification_small_imageview_previous,
                                previousPendingIntent);
                mSmallNotificationView
                        .setOnClickPendingIntent(R.id.notification_small_imageview_next,
                                nextPendingIntent);

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                    mLargeNotificationView
                            .setOnClickPendingIntent(R.id.notification_large_imageview_previous,
                                    previousPendingIntent);
                    mLargeNotificationView
                            .setOnClickPendingIntent(R.id.notification_large_imageview_next,
                                    nextPendingIntent);
                }
            }

            NotificationCompat.Builder builder = new NotificationCompat.Builder(
                    PlayerService.this)
                    .setSmallIcon(R.drawable.tray).setContentTitle(SoundtapeApplication.APP_NAME)
                    .setContentText(streamTitle).setOngoing(true).setPriority(
                            NotificationCompat.PRIORITY_MAX).setContent(mSmallNotificationView);

            notification = builder.build();

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                mLargeNotificationView
                        .setOnClickPendingIntent(R.id.notification_large_imageview_playpause,
                                playPausePendingIntent);
                mLargeNotificationView
                        .setOnClickPendingIntent(R.id.notification_large_imageview_mode,
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
                        SoundtapeApplication.APP_NAME);
                mLargeNotificationView
                        .setTextViewText(R.id.notification_large_textview2, streamTitle);
            }


            drawPlayPause();
            drawMode();
            updateNotification();
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        // TODO: Return the communication channel to the service.
        throw new UnsupportedOperationException("Not yet implemented");
    }

    public void updateMusicList() {
        synchronized (canUpdateMusicList) {
            if (!requestMp3.isInterrupted()) {
                if (type.equals(SoundtapeApplication.TYPE_MUSIC)) {
                    MusicList.addFirst(new Music(streamTitle, cover, ytUrl));
                } else if (type.equals(SoundtapeApplication.TYPE_PLAYLIST)) {
                    MusicList.addFirst(new Music(streamTitle, cover, playlist.getCurrentMusicId()));
                }
                notifyHistoryActivity();
                MusicList.saveMusicList(getSharedPreferences(SoundtapeApplication.PREFS_NAME, Context.MODE_PRIVATE));
            }
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


    public void saveMode() {
        SharedPreferences preferences = getSharedPreferences(SoundtapeApplication.PREFS_NAME, 0);
        SharedPreferences.Editor editor = preferences.edit();
        if (type.equals(SoundtapeApplication.TYPE_MUSIC)) {
            editor.putString(SoundtapeApplication.PREFS_MODE_MUSIC, mode);
        } else if (type.equals(SoundtapeApplication.TYPE_PLAYLIST)) {
            editor.putString(SoundtapeApplication.PREFS_MODE_PLAYLIST, mode);
        }
        editor.commit();
    }

    public void loadMode() {
        SharedPreferences preferences = getSharedPreferences(SoundtapeApplication.PREFS_NAME, 0);
        if (type.equals(SoundtapeApplication.TYPE_MUSIC)) {
            mode = preferences.getString(SoundtapeApplication.PREFS_MODE_MUSIC, MODE_NORMAL);
        } else if (type.equals(SoundtapeApplication.TYPE_PLAYLIST)) {
            mode = preferences.getString(SoundtapeApplication.PREFS_MODE_PLAYLIST, MODE_NORMAL);
        }
        if (mode.equals(MODE_LOOPONE)) {
            mediaPlayer.setLooping(true);
        } else if (mode.equals(MODE_SHUFFLE)) {
            playlist.setShuffle(true);
        }
    }

    private class AudioManagerListener implements AudioManager.OnAudioFocusChangeListener {

        @Override
        public void onAudioFocusChange(int focusChange) {
            switch (focusChange) {
                case AudioManager.AUDIOFOCUS_GAIN:
                    Logger.i(SoundtapeApplication.TAG, "AUDIOFOCUS_GAIN");
                    // Set volume level to desired levels
                    // returnVolumeToNormal();
                    break;
                case AudioManager.AUDIOFOCUS_GAIN_TRANSIENT:
                    Logger.i(SoundtapeApplication.TAG, "AUDIOFOCUS_GAIN_TRANSIENT");
                    // Set volume level to desired levels
                    // returnVolumeToNormal();
                    play();
                    break;
                case AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK:
                    Logger.i(SoundtapeApplication.TAG, "AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK");
                    // Set volume level to desired levels
                    // returnVolumeToNormal();
                    play();
                    break;
                case AudioManager.AUDIOFOCUS_LOSS:
                    Logger.e(SoundtapeApplication.TAG, "AUDIOFOCUS_LOSS");
                    // Lower the volume
                    pause();
                    break;
                case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
                    Logger.e(SoundtapeApplication.TAG, "AUDIOFOCUS_LOSS_TRANSIENT");
                    // Lower the volume
                    pause();
                    break;
                case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK:
                    Logger.e(SoundtapeApplication.TAG, "AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK");
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
                /*Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("http://www.scumtube.com"));
                browserIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(browserIntent);
                PlayerService.this.exit();
                return;*/
            }
			if (requestMp3Task.hadSuccess()) {
                streamTitle = requestMp3Task.getTitle();
                streamCoverUrl = requestMp3Task.getCoverUrl();
                streamMp3Url = requestMp3Task.getMp3Url();
                if (this.isInterrupted()) {
                    return;
                }
                synchronized (canExitLock) {
                    try {
                        Logger.i(SoundtapeApplication.TAG, "Drawing notification player...");
                        PlayerService.this.start();
                        Logger.i(SoundtapeApplication.TAG, "Finished drawing notification player.");
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                            downloadImageTask = new DownloadImageTask(streamCoverUrl);
                            if (this.isInterrupted()) {
                                return;
                            }
                            downloadImageTask.start();
                        } else {
                            updateMusicList();
                        }
                    } catch (Exception e) {
                        Logger.i(SoundtapeApplication.TAG, e.getClass().getName(), e);
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

    private final class RequestPlaylist extends Thread {

        private final RequestPlaylistTask requestPlaylistTask;
        private DownloadImageTask downloadImageTask;

        public RequestPlaylist(String playlistId) {
            requestPlaylistTask = new RequestPlaylistTask(playlistId);
        }

        @Override
        public void run() {
            requestPlaylistTask.start();
            try {
                requestPlaylistTask.waitUntilFinished();
            } catch (InterruptedException e) {
                requestPlaylistTask.interrupt();
                return;
            }
            if (requestPlaylistTask.needsUpdate()) {
                showToast(requestPlaylistTask.getMessage());
                /*Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("http://www.scumtube.com"));
                browserIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(browserIntent);
                PlayerService.this.exit();
                return;*/
            }
			if (requestPlaylistTask.hadSuccess()) {
                final ArrayList<String> videoIds = requestPlaylistTask.getVideoIds();
                playlist = new Playlist(videoIds);
                if (this.isInterrupted()) {
                    return;
                }
                synchronized (canExitLock) {
                    requestCurrent();
                }

                return;
            } else {
                showToast(requestPlaylistTask.getMessage());
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
            Logger.i(SoundtapeApplication.TAG, "Loading cover image...");
            try {
                Bitmap image = null;
                try {
                    InputStream in = new java.net.URL(imageUrl).openStream();
                    image = BitmapFactory.decodeStream(in);
                } catch (Exception e) {
                    Logger.e("Error", e.getMessage());
                    e.printStackTrace();
                }
                cover = image;

                drawCover();
                updateMusicList();
            } catch (Exception e) {
                if (e.getMessage() != null)
                    Logger.i(SoundtapeApplication.TAG, e.getMessage());
            }
            Logger.i(SoundtapeApplication.TAG, "Finished loading cover image.");
        }
    }

    private class PhoneCallListener extends PhoneStateListener {

        private long mStartCallTime = 0L;

        @Override
        public void onCallStateChanged(int state, String incomingNumber) {
            switch (state) {
                case TelephonyManager.CALL_STATE_RINGING:
                    if (mediaPlayer.isPlaying()) {
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
