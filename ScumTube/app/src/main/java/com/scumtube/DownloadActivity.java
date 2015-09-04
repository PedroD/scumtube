package com.scumtube;

import android.content.Intent;
import android.os.Bundle;

public class DownloadActivity extends AbstractActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getIntent().getExtras() != null) {
            final Bundle extras = getIntent().getExtras();
            final String ytUrl = extras.getString(Intent.EXTRA_TEXT);

            if (ytUrl != null && ytUrl.contains("http")) {
                final Intent downloadService = new Intent(this, DownloadService.class);
                if (ytUrl.contains("list")) {
                    showToast("ScumTube doesn't support playlist download yet.");
                    moveTaskToBack(true);
                    finish();
                    return;
                }
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
