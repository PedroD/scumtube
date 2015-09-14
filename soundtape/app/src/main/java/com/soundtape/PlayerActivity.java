package com.soundtape;

import android.content.Intent;
import android.os.Bundle;

import com.google.android.gms.analytics.HitBuilders;
import com.google.android.gms.analytics.Tracker;


public class PlayerActivity extends AbstractActivity {
    private Tracker mTracker;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mTracker = ((SoundtapeApplication)getApplication()).getDefaultTracker();
        mTracker.setScreenName("PlayerActivity");
        if (getIntent().getExtras() != null) {
            final Bundle extras = getIntent().getExtras();
            final String ytUrl = extras.getString(Intent.EXTRA_TEXT);
            if (ytUrl != null && ytUrl.contains("http")) {
                final Intent playerService = new Intent(this, PlayerService.class);
                if(ytUrl.contains("list")){
                    mTracker.send(new HitBuilders.EventBuilder()
                            .setCategory("Play")
                            .setAction("Playlist")
                            .build());
                    playerService.putExtra("type", SoundtapeApplication.TYPE_PLAYLIST);
                } else {
                    mTracker.send(new HitBuilders.EventBuilder()
                            .setCategory("Play")
                            .setAction("Music")
                            .build());
                    playerService.putExtra("type", SoundtapeApplication.TYPE_MUSIC);
                }
                playerService.putExtra("ytUrl", ytUrl);
                startService(playerService);
            } else {
                showToast("Error: Wrong URL (" + ytUrl + ")!");
            }
        } else {
            showToast("An error occurred!");
        }
        moveTaskToBack(true);
        finish();
    }

}
