package com.scumtube;

import android.content.Intent;
import android.os.Bundle;


public class PlayerActivity extends AbstractActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getIntent().getExtras() != null) {
            final Bundle extras = getIntent().getExtras();
            final String ytUrl = extras.getString(Intent.EXTRA_TEXT);
            if (ytUrl != null && ytUrl.contains("http")) {
                final Intent playerService = new Intent(this, PlayerService.class);
                if(ytUrl.contains("list")){
                    playerService.putExtra("type", ScumTubeApplication.TYPE_PLAYLIST);
                } else {
                    playerService.putExtra("type", ScumTubeApplication.TYPE_MUSIC);
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
