package com.soundtape;

import android.content.Intent;
import android.os.Bundle;

import com.google.android.gms.analytics.HitBuilders;
import com.google.android.gms.analytics.Tracker;

public class DownloadActivity extends AbstractActivity {

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
                final Intent downloadService = new Intent(this, DownloadService.class);
                if (ytUrl.contains("list")) {
                    mTracker.send(new HitBuilders.EventBuilder()
                            .setCategory("Download")
                            .setAction("Playlist")
                            .build());
                    showToast("soundtape doesn't support playlist download yet.");
                    moveTaskToBack(true);
                    finish();
                    return;
                }
                mTracker.send(new HitBuilders.EventBuilder()
                        .setCategory("Download")
                        .setAction("Music")
                        .build());
                downloadService.putExtra("ytUrl", ytUrl);
                startService(downloadService);
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
