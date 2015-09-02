package com.scumtube;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.PopupMenu;
import android.widget.TextView;

import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdView;

import java.util.ArrayList;

public class HistoryActivity extends AbstractActivity {

    private static MusicArrayAdapter adapter;
    private static ListView listView;
    public static boolean hasView = false;
    private static ArrayList<Music> musicArrayList;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_history);

        AdView mAdView = (AdView) findViewById(R.id.adView);
        //AdRequest adRequest = new AdRequest.Builder().addTestDevice("8466E20A350086795264CE662B18DC59").addTestDevice("58B2FFDEEAA2B8B16A6A3969DCEB6570").build(); //TODO remove addTestDevice for production
        AdRequest adRequest = new AdRequest.Builder().build();
        mAdView.loadAd(adRequest);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu items for use in the action bar
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.menu_history, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.history_menu_delete_all:
                AlertDialog.Builder builder = new AlertDialog.Builder(new ContextThemeWrapper(this, android.R.style.Theme_Holo_Dialog));
                builder.setMessage(R.string.history_menu_delete_all_message)
                        .setTitle(R.string.history_menu_delete_all)
                        .setPositiveButton(R.string.history_menu_delete_all, new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int id) {
                                MusicList.removeAll();
                                saveMusicList();
                                getView();
                            }
                        })
                        .setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int id) {
                            }
                        }).create().show();
                ;
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    public void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (intent.getExtras() != null) {
            final Bundle extras = intent.getExtras();
            final String extraText = extras.getString(Intent.EXTRA_TEXT);
            if (extraText != null && extraText.equals(PlayerService.EXTRA_DATASETCHANGED)) {
                Logger.i(ScumTubeApplication.TAG, "Intent");
                adapter.notifyDataSetChanged();
            }
        } else {
            Logger.i(ScumTubeApplication.TAG, "getView");
            getView();
        }
    }


    public void getView() {
        MusicList.loadMusicList(getSharedPreferences(ScumTubeApplication.PREFS_NAME, Context.MODE_PRIVATE));

        createAdapter();

        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {

            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                final Intent playerService = new Intent(getApplicationContext(), PlayerService.class);
                playerService.putExtra("ytUrl", musicArrayList.get(position).getYtUrl());
                playerService.putExtra("type", ScumTubeApplication.TYPE_MUSIC);
                startService(playerService);
                sendHomeIntent();
            }
        });

        listView.setOnItemLongClickListener(
                new AdapterView.OnItemLongClickListener() {

                    @Override
                    public boolean onItemLongClick(final AdapterView<?> parent, View view, final int position, long id) {
                        PopupMenu popup = new PopupMenu(HistoryActivity.this, view.findViewById(R.id.history_item_title));
                        popup.getMenuInflater().inflate(R.menu.menu_history_item, popup.getMenu());
                        popup.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
                            public boolean onMenuItemClick(MenuItem item) {
                                int id = item.getItemId();
                                switch (id) {
                                    case R.id.history_menu_delete:
                                        Music musicItem = (Music) parent.getItemAtPosition(position);
                                        MusicList.remove(musicItem);
                                        saveMusicList();
                                        getView();
                                }
                                return true;
                            }
                        });
                        popup.show();
                        return true;
                    }
                }
        );

        hasView = true;
    }

    public void saveMusicList() {
        MusicList.saveMusicList(getSharedPreferences(ScumTubeApplication.PREFS_NAME, Context.MODE_PRIVATE));
    }

    public void createAdapter() {

        listView = (ListView) findViewById(R.id.history_listview);
        listView.setEmptyView((View) findViewById(R.id.history_empty));
        musicArrayList = MusicList.getMusicArrayList();

        adapter = new MusicArrayAdapter(this,
                android.R.layout.simple_list_item_1, musicArrayList);
        listView.setAdapter(adapter);
    }

    @Override
    public void onResume() {
        super.onResume();
        Logger.i(ScumTubeApplication.TAG, "onResume");
        if (!hasView) {
            getView();
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        hasView = false;
    }

    @Override
    public void finish() {
        super.finish();
        hasView = false;
    }


    public void sendHomeIntent() {
        Intent i = new Intent(Intent.ACTION_MAIN);
        i.addCategory(Intent.CATEGORY_HOME);
        startActivity(i);
    }

    private class MusicArrayAdapter extends ArrayAdapter<Music> {


        public MusicArrayAdapter(Context context, int textViewResourceId, ArrayList<Music> objects) {
            super(context, textViewResourceId, objects);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View v = convertView;
            ViewHolder holder;
            if (v == null) {

                LayoutInflater vi = LayoutInflater.from(getContext());
                v = vi.inflate(R.layout.history_item, null);

                holder = new ViewHolder();

                holder.cover = (ImageView) v.findViewById(R.id.history_item_cover);
                holder.title = (TextView) v.findViewById(R.id.history_item_title);

                v.setTag(holder);

            } else {
                holder = (ViewHolder) v.getTag();
            }

            Music item = getItem(position);

            holder.cover.setImageBitmap(item.getCover());
            holder.title.setText(item.getTitle());
            return v;
        }
    }

    private static class ViewHolder {
        ImageView cover;
        TextView title;
    }


}
