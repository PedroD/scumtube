package com.scumtube;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

public class DownloadActivity extends AbstractActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getIntent().getExtras() != null) {
            final Bundle extras = getIntent().getExtras();
            final String ytUrl = extras.getString(Intent.EXTRA_TEXT);
            if (ytUrl != null && ytUrl.contains("http")) {
                final Intent downloadService = new Intent(this, DownloadService.class);
                downloadService.putExtra("ytUrl", ytUrl);
                startService(downloadService);
            } else {
                Toast.makeText(getApplicationContext(), "Error: Wrong URL (" + ytUrl + ")!", Toast.LENGTH_LONG).show();
            }
        } else {
            Toast.makeText(getApplicationContext(), "An error occurred!", Toast.LENGTH_LONG).show();
        }
        moveTaskToBack(true);
        finish();
    }
}
