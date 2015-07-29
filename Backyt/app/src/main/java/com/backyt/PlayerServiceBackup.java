package com.backyt;

import android.app.Service;
import android.content.Intent;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.IBinder;
import android.util.Log;

import java.io.IOException;

public class PlayerServiceBackup extends Service implements MediaPlayer.OnPreparedListener {

    private static final String ACTION_PLAY = "com.example.action.PLAY";
    MediaPlayer mediaPlayer = null;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String url = "http://wavedomotics.com/-SwQr1Xsg9E.mp3"; // your URL here
        mediaPlayer = new MediaPlayer();
        mediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
        try {
            mediaPlayer.setDataSource(this, Uri.parse(url));
        } catch (IOException e) {
            e.printStackTrace();
        }
        try {
            mediaPlayer.prepare(); // might take long! (for buffering, etc)
        } catch (IOException e) {
            e.printStackTrace();
        }
        mediaPlayer.start();
        Log.i("debug", "Caralho");
        return super.onStartCommand(intent, flags, startId);
    }

    /** Called when MediaPlayer is ready */
    public void onPrepared(MediaPlayer player) {
        Log.i("debug", "onPrepared");

        player.start();

        Log.i("debug", "start");


    }


    public PlayerServiceBackup() {
    }

    @Override
    public IBinder onBind(Intent intent) {
        // TODO: Return the communication channel to the service.
        throw new UnsupportedOperationException("Not yet implemented");
    }


}
