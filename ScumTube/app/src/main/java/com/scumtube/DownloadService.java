package com.scumtube;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Environment;
import android.os.IBinder;
import android.support.v4.app.NotificationCompat;
import android.util.Log;
import android.widget.RemoteViews;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;

public class DownloadService extends AbstractService {

    private int currentNotificationId = 2;

    private final String ACTION_EXIT = "com.scumtube.ACTION_EXIT";
    private final String ACTION_OPENMUSIC = "com.scumtube.ACTION_OPENMUSIC";

    private ArrayList<MusicDownloading> musicDownloadingArrayList = new ArrayList<MusicDownloading>();
    private CheckProgress checkProgress;
    private boolean externalStorageWriteable = false;

    public DownloadService() {
        checkProgress = new CheckProgress();
        checkProgress.start();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.i(ScumTubeApplication.TAG, "On start command download invoked: " + intent);

        final String title = intent.getStringExtra("title");
        final String ytUrl = intent.getStringExtra("ytUrl");
        final String mp3Url = intent.getStringExtra("mp3Url");

        updateExternalStorageState();

        if (intent.getAction() != null) {
            if (intent.getAction().equals(ACTION_EXIT)) {
                if (intent.hasExtra("notificationId")) {
                    exit(intent.getExtras().getInt("notificationId"));
                }
            } else if (intent.getAction().equals(ACTION_OPENMUSIC)) {
                if (intent.hasExtra("notificationId")) {
                    int notificationId = intent.getExtras().getInt("notificationId");
                    final MusicDownloading m = getMusicDownloadingByNotificationId(notificationId);
                    if (m != null) {
                        if (m.getFilePath() != null) {
                            final File file = new File(m.getFilePath());
                            if (file.exists()) {
                                final Intent it = new Intent(Intent.ACTION_CLOSE_SYSTEM_DIALOGS);
                                getApplicationContext().sendBroadcast(it);
                                final Intent openMusicIntent = new Intent();
                                openMusicIntent.setAction(android.content.Intent.ACTION_VIEW);
                                openMusicIntent.setDataAndType(Uri.fromFile(file), "audio/*");
                                openMusicIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                                startActivity(openMusicIntent);
                                exit(notificationId);
                            } else {
                                showToast("The file doesn't exist.");
                            }
                        }
                    } else {
                        showToast("There was an error opening the file.");
                    }
                } else {
                    showToast("There was an error opening the file.");
                }
            }
        } else if (!externalStorageWriteable) {
            showToast("Can't write on the external storage.");
        } else if (title != null && ytUrl != null && mp3Url != null) {
            if (!isAlreadyBeingDownloaded(ytUrl)) {
                musicDownloadingArrayList.add(new MusicDownloading(new Music(title, ytUrl), currentNotificationId, mp3Url));
                currentNotificationId++;
            } else {
                showToast("The music is already being downloaded.");
            }
        } else {
            showToast("There was an error downloading the music. Try again.");
        }
        return START_NOT_STICKY;
    }

    private void exit(int notificationId) {
        Log.i(ScumTubeApplication.TAG, "Exiting: " + notificationId);

        final MusicDownloading m = getMusicDownloadingByNotificationId(notificationId);
        if (m != null) {
            Log.i(ScumTubeApplication.TAG, "Found exiting: " + m.getMusic().getTitle() + " :: " + " :: " + notificationId);
            musicDownloadingArrayList.remove(m);
            m.exit();
            return;
        }
    }

    private MusicDownloading getMusicDownloadingByNotificationId(int notificationId) {
        for (MusicDownloading m : musicDownloadingArrayList) {
            if (notificationId == m.getNotificationId()) {
                return m;
            }
        }
        return null;
    }

    private void updateExternalStorageState() {
        final String state = Environment.getExternalStorageState();
        if (Environment.MEDIA_MOUNTED.equals(state)) {
            externalStorageWriteable = true;
        } else if (Environment.MEDIA_MOUNTED_READ_ONLY.equals(state)) {
            externalStorageWriteable = false;
        } else {
            externalStorageWriteable = false;
        }
    }

    public boolean isAlreadyBeingDownloaded(String ytUrl) {
        for (MusicDownloading m : musicDownloadingArrayList) {
            if (m.getMusic().getYtUrl().equals(ytUrl)) {
                return true;
            }
        }
        return false;
    }

    private class MusicDownloading {
        private final Music music;
        private final int notificationId;
        private final DownloadMp3 downloadingThread;
        private boolean isDone = false;
        private String filePath;

        public MusicDownloading(Music music, int notificationId, String mp3Url) {
            this.music = music;
            this.notificationId = notificationId;
            downloadingThread = new DownloadMp3(music.getTitle(), mp3Url, notificationId, createDownloadNotification(android.R.drawable.stat_sys_download));
            downloadingThread.start();
        }

        public Notification createDownloadNotification(int icon) {
            Log.i(ScumTubeApplication.TAG, "Creating download progress notification: " + music.getTitle());

            final Intent intent = new Intent(ACTION_EXIT, null, DownloadService.this,
                    DownloadService.class);
            intent.putExtra("notificationId", notificationId);
            final PendingIntent exitPendingIntent = PendingIntent
                    .getService(DownloadService.this, notificationId, intent,
                            PendingIntent.FLAG_UPDATE_CURRENT);

            final RemoteViews notificationView = createDownloadNotificationView();
            notificationView
                    .setOnClickPendingIntent(R.id.notification_download_imageview_exit,
                            exitPendingIntent);

            NotificationCompat.Builder builder = new NotificationCompat.Builder(
                    DownloadService.this)
                    .setSmallIcon(icon).setContentTitle(ScumTubeApplication.APP_NAME)
                    .setOngoing(true).setPriority(NotificationCompat.PRIORITY_MAX)
                    .setContent(notificationView);

            final Notification notification = builder.build();

            final NotificationManager mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            mNotificationManager.notify(notificationId, notification);

            return notification;
        }

        public RemoteViews createDownloadNotificationView() {
            RemoteViews downloadNotificationView = new RemoteViews(getPackageName(), R.layout.notification_download);
            downloadNotificationView.setTextViewText(R.id.notification_download_textview, music.getTitle());
            return downloadNotificationView;
        }

        public void updateNotification(Notification notification, int notificationId, int progress) {
            if (progress < 100) {
                notification.contentView.setProgressBar(R.id.notification_download_progressbar, 100, progress, false);
                notification.contentView.setTextViewText(R.id.notification_download_textview_progress, progress + "%");
                final NotificationManager mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
                mNotificationManager.notify(notificationId, notification);
            } else {
                Notification n = createDownloadNotification(android.R.drawable.stat_sys_download_done);
                n.contentView.setProgressBar(R.id.notification_download_progressbar, 100, progress, false);
                n.contentView.setTextViewText(
                        R.id.notification_download_textview_progress, progress + "% " + getString(R.string.download_done));
                Intent intent = new Intent(ACTION_OPENMUSIC, null, DownloadService.this,
                        DownloadService.class);
                intent.putExtra("notificationId", notificationId);
                PendingIntent exitPendingIntent = PendingIntent
                        .getService(DownloadService.this, notificationId, intent,
                                PendingIntent.FLAG_UPDATE_CURRENT);
                n.contentView
                        .setOnClickPendingIntent(R.id.notification_download_wrapper,
                                exitPendingIntent);
                final NotificationManager mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
                mNotificationManager.notify(notificationId, n);
            }
        }

        public void exit() {
            downloadingThread.interrupt();
            final NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            nm.cancel(notificationId);
        }

        public DownloadMp3 getDownloadingThread() {
            return downloadingThread;
        }

        public Music getMusic() {
            return music;
        }

        public int getNotificationId() {
            return notificationId;
        }

        public boolean isDone() {
            return isDone;
        }

        public void setIsDone(boolean isDone) {
            this.isDone = isDone;
        }

        public String getFilePath() {
            return filePath;
        }

        public void setFilePath(String filePath) {
            this.filePath = filePath;
        }
    }

    class DownloadMp3 extends Thread {
        private final String title;
        private final String mp3Url;
        private final int notificationId;
        private final Notification notification;
        private int progress;

        public DownloadMp3(String title, String mp3Url, int notificationId, Notification notification) {
            this.title = title;
            this.mp3Url = mp3Url;
            this.notificationId = notificationId;
            this.notification = notification;
        }

        public int getProgress() {
            return progress;
        }

        public Notification getNotification() {
            return notification;
        }

        public int getNotificationId() {
            return notificationId;
        }

        @Override
        public void run() {
            try {
                String titleEscaped = addEscapeChars(title);
                Log.i(ScumTubeApplication.TAG, "Started the download thread of: " + titleEscaped + " :: " + mp3Url + " :: " + notificationId);
                URL url = new URL(mp3Url);
                URLConnection connection = url.openConnection();
                connection.connect();
                // this will be useful so that you can show a typical 0-100% progress bar
                int fileLength = connection.getContentLength();

                if (isInterrupted()) {
                    Log.i(ScumTubeApplication.TAG, "Interrupted the download thread of: " + titleEscaped + " :: " + mp3Url + " :: " + notificationId);
                    return;
                }

                // download the file
                InputStream input = new BufferedInputStream(connection.getInputStream());
                String directoryPath = Environment.getExternalStorageDirectory() + "/ScumTube/";
                File directory = new File(directoryPath);
                if (!directory.exists()) {
                    directory.mkdir();
                }

                String filePath = directoryPath + titleEscaped + ".mp3";

                Log.i(ScumTubeApplication.TAG, "Output directory: " + filePath);


                File file;
                int i = 1;
                while (true) {
                    file = new File(filePath);
                    if (!file.exists()) {
                        break;
                    }
                    Log.i(ScumTubeApplication.TAG, "The file already exists: " + filePath);
                    filePath = directoryPath + titleEscaped + "(" + i + ")" + ".mp3";
                    i++;
                }

                OutputStream output = new FileOutputStream(filePath);

                if (isInterrupted()) {
                    Log.i(ScumTubeApplication.TAG, "Interrupted the download thread of: " + titleEscaped + " :: " + mp3Url + " :: " + notificationId);
                    output.close();
                    input.close();
                    return;
                }

                byte data[] = new byte[1024];
                long total = 0;
                int count;
                while ((count = input.read(data)) != -1) {
                    total += count;
                    progress = (int) (total * 100 / fileLength);
                    output.write(data, 0, count);
                    if (isInterrupted()) {
                        Log.i(ScumTubeApplication.TAG, "Interrupted the download thread of: " + titleEscaped + " :: " + mp3Url + " :: " + notificationId);
                        output.close();
                        input.close();
                        new File(filePath).delete();
                        return;
                    }
                }
                output.flush();
                output.close();
                input.close();
                MusicDownloading m = getMusicDownloadingByNotificationId(notificationId);
                m.setFilePath(filePath);
                Log.i(ScumTubeApplication.TAG, "Finished the download thread of: " + titleEscaped + " :: " + mp3Url + " :: " + notificationId);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        private String addEscapeChars(String s){
            String result = s.replaceAll("\\t", "");
            result = result.replaceAll("\\b", "");
            result = result.replaceAll("\\n", "");
            result = result.replaceAll("\\r", "");
            result = result.replaceAll("\\f", "");
            result = result.replaceAll("'", "");
            result = result.replaceAll("\"", "");
            result = result.replaceAll("\\\\", "");
            result = result.replaceAll("/", "");
            return result;
        }
    }

    class CheckProgress extends Thread {

        @Override
        public void run() {
            while (!isInterrupted()) {
                for (MusicDownloading m : musicDownloadingArrayList) {
                    if (!isInterrupted() && !m.isDone()) {
                        m.updateNotification(m.getDownloadingThread().getNotification(),
                                m.getDownloadingThread().getNotificationId(),
                                m.getDownloadingThread().getProgress());
                    }
                    if (m.getDownloadingThread().getProgress() == 100) {
                        m.setIsDone(true);
                    }
                }
                try {
                    sleep(2000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        // TODO: Return the communication channel to the service.
        throw new UnsupportedOperationException("Not yet implemented");
    }
}
