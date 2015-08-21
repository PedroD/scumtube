package com.scumtube;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
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

    private static int DOWNLOADSERVICE_NOTIFICATION_ID = 2;

    private static final String ACTION_EXIT = "com.scumtube.ACTION_EXIT";

    private ArrayList<MusicDownloading> musicDownloadingArrayList = new ArrayList<MusicDownloading>();

    private CheckProgress checkProgress;

    private String title;
    private String ytUrl;
    private String mp3Url;

    private boolean externalStorageWriteable = false;

    private class MusicDownloading {
        private Music music;
        private int notificationId;
        private DownloadMp3 downloadingThread;

        public MusicDownloading(Music music, int notificationId, DownloadMp3 downloadingThread) {
            this.music = music;
            this.notificationId = notificationId;
            this.downloadingThread = downloadingThread;
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
    }

    public DownloadService() {
        checkProgress = new CheckProgress();
        checkProgress.start();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.i(ScumTubeApplication.TAG, "On start command download invoked: " + intent);

        title = intent.getStringExtra("title");
        ytUrl = intent.getStringExtra("ytUrl");
        mp3Url = intent.getStringExtra("mp3Url");

        updateExternalStorageState();

        if (intent.getAction() != null) {
            if(intent.getAction().equals(ACTION_EXIT)) {
                if (intent.hasExtra("notificationId")) {
                    Log.i(ScumTubeApplication.TAG, "Exiting: " + intent.getExtras().getInt("notificationId"));
                    exit(intent.getExtras().getInt("notificationId"));
                }
            }
        } else if (!externalStorageWriteable) {
            showToast("Can't write on the external storage.");
        } else if (title == null || ytUrl == null || mp3Url == null) {
            showToast("There was an error downloading the music. Try again.");
        } else {
            if (!isAlreadyBeingDownloaded(ytUrl)) {
                DownloadMp3 downloadThread = new DownloadMp3(title, mp3Url, DOWNLOADSERVICE_NOTIFICATION_ID, createDownloadNotification());
                musicDownloadingArrayList.add(new MusicDownloading(new Music(title, ytUrl), DOWNLOADSERVICE_NOTIFICATION_ID, downloadThread));
                downloadThread.start();
                DOWNLOADSERVICE_NOTIFICATION_ID++;
            } else {
                showToast("The music is already being downloaded.");
            }
        }
        return START_NOT_STICKY;
    }

    private void exit(int notificationId) {
        for (MusicDownloading m : musicDownloadingArrayList) {
            if(m.getNotificationId() == notificationId){
                musicDownloadingArrayList.remove(m);
                m.getDownloadingThread().interrupt();
                final NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
                nm.cancel(m.getNotificationId());
                return;
            }

        }
    }

    private void updateExternalStorageState() {
        String state = Environment.getExternalStorageState();
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


    public Notification createDownloadNotification() {
        Log.i(ScumTubeApplication.TAG, "Creating download progress notification: " + title);

        Intent intent = new Intent(ACTION_EXIT, null, DownloadService.this,
                DownloadService.class);
        intent.putExtra("notificationId", DOWNLOADSERVICE_NOTIFICATION_ID);
        PendingIntent exitPendingIntent = PendingIntent
                .getService(DownloadService.this, 0, intent,
                        PendingIntent.FLAG_UPDATE_CURRENT);

        RemoteViews notificationView = createDownloadNotificationView();
        notificationView
                .setOnClickPendingIntent(R.id.notification_download_imageview_exit,
                        exitPendingIntent);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(
                DownloadService.this)
                .setSmallIcon(R.drawable.ic_loading).setContentTitle(ScumTubeApplication.APP_NAME)
                .setOngoing(true).setPriority(NotificationCompat.PRIORITY_MAX)
                .setContent(notificationView);

        Notification notification = builder.build();

        final NotificationManager mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        mNotificationManager.notify(DOWNLOADSERVICE_NOTIFICATION_ID, notification);

        return notification;
    }

    public RemoteViews createDownloadNotificationView() {
        RemoteViews downloadNotificationView = new RemoteViews(getPackageName(), R.layout.notification_download);
        downloadNotificationView.setTextViewText(R.id.notification_download_textview, title);
        return downloadNotificationView;
    }

    public void updateNotification(Notification notification, int notificationId, int progress) {
        notification.contentView.setProgressBar(R.id.notification_download_progressbar, 100, progress, false);

        final NotificationManager mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        mNotificationManager.notify(notificationId, notification);
    }

    class DownloadMp3 extends Thread {
        private String title;
        private String mp3Url;
        private int notificationId;
        private Notification notification;
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
                Log.i(ScumTubeApplication.TAG, "Started the download thread of: " + title + " :: " + mp3Url + " :: " + notificationId);
                URL url = new URL(mp3Url);
                URLConnection connection = url.openConnection();
                connection.connect();
                // this will be useful so that you can show a typical 0-100% progress bar
                int fileLength = connection.getContentLength();

                if(isInterrupted()){
                    Log.i(ScumTubeApplication.TAG, "Interrupted the download thread of: " + title + " :: " + mp3Url + " :: " + notificationId);
                    return;
                }
                Log.i(ScumTubeApplication.TAG, "Output directory: " + Environment.getExternalStorageDirectory() + "/" + title + ".mp3");

                // download the file
                InputStream input = new BufferedInputStream(connection.getInputStream());
                String filePath = Environment.getExternalStorageDirectory() + "/" + title + ".mp3";
                OutputStream output = new FileOutputStream(filePath);

                if(isInterrupted()){
                    Log.i(ScumTubeApplication.TAG, "Interrupted the download thread of: " + title + " :: " + mp3Url + " :: " + notificationId);
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
                    if(isInterrupted()){
                        Log.i(ScumTubeApplication.TAG, "Interrupted the download thread of: " + title + " :: " + mp3Url + " :: " + notificationId);
                        output.close();
                        input.close();
                        new File(filePath).delete();
                        return;
                    }
                }

                output.flush();
                output.close();
                input.close();
                Log.i(ScumTubeApplication.TAG, "Finished the download thread of: " + title + " :: " + mp3Url + " :: " + notificationId);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    class CheckProgress extends Thread {

        @Override
        public void run() {
            while (!isInterrupted()) {
                for (MusicDownloading m : musicDownloadingArrayList) {
                    if (!isInterrupted()) {
                        updateNotification(m.getDownloadingThread().getNotification(),
                                m.getDownloadingThread().getNotificationId(),
                                m.getDownloadingThread().getProgress());
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
