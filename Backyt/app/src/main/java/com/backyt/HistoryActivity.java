package com.backyt;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Base64;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;

public class HistoryActivity extends AppCompatActivity {

    private static MusicArrayAdapter adapter;
    private static ListView listView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getIntent().getExtras() != null) {
            final Bundle extras = getIntent().getExtras();
            final String ytUrl = extras.getString(Intent.EXTRA_TEXT);
            if (ytUrl != null && ytUrl.contains("http")) {
                final Intent playerService = new Intent(this, PlayerService.class);
                playerService.putExtra("ytUrl", ytUrl);
                startService(playerService);
                moveTaskToBack(true);
                finish();
            } else {
                Toast.makeText(getApplicationContext(), "Error: Wrong URL (" + ytUrl + ")!", Toast.LENGTH_LONG).show();
            }
        } else {
            // Toast.makeText(getApplicationContext(), "An error occurred!", Toast.LENGTH_LONG).show();

            loadMusicList();

            setContentView(R.layout.activity_history);

            listView = (ListView) findViewById(R.id.history_listview);
            final ArrayList<Music> musicArrayList = MusicList.getMusicArrayList();

            adapter = new MusicArrayAdapter(this,
                    android.R.layout.simple_list_item_1, musicArrayList);
            listView.setAdapter(adapter);

            listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {

                @Override
                public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                    final Intent playerService = new Intent(HistoryActivity.this, PlayerService.class);
                    playerService.putExtra("ytUrl", musicArrayList.get(position).getYtUrl());
                    startService(playerService);
                    finish();
                }
            });
        }


    }

    @Override
    public void onResume() {
        super.onResume();
        adapter.notifyDataSetChanged();
    }

    private class MusicArrayAdapter extends ArrayAdapter<Music> {


        public MusicArrayAdapter(Context context, int textViewResourceId, ArrayList<Music> objects) {
            super(context, textViewResourceId, objects);
        }

      /*  @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View view = convertView;

            if (view == null) {
                LayoutInflater viewInflater;
                viewInflater = LayoutInflater.from(getContext());
                view = viewInflater.inflate(R.layout.history_item, null);
            }

            Music item = getItem(position);

            if (item != null) {
                ImageView cover = (ImageView) view.findViewById(R.id.history_item_cover);
                TextView title = (TextView) view.findViewById(R.id.history_item_title);

                if (cover != null) {
                    cover.setImageBitmap(item.getCover());
                }

                if (title != null) {
                    title.setText(item.getTitle());
                }
            }
            return view;
        }*/

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

            Log.i(PlayerService.TAG, item.getTitle() + " position:" + position);

            holder.cover.setImageBitmap(item.getCover());
            holder.title.setText(item.getTitle());
            return v;
        }
    }

    private static class ViewHolder {
        ImageView cover;
        TextView title;
    }


    public void loadMusicList() {
        if(MusicList.getMusicArrayList().isEmpty()) {
            SharedPreferences preferences = getSharedPreferences(PlayerService.PREFS_NAME, Context.MODE_PRIVATE);
            String musicJsonArrayString = preferences.getString(PlayerService.PREFS_MUSICLIST, null);
            if (musicJsonArrayString != null) {
                try {
                    Log.d(PlayerService.TAG, "Load array string: " + musicJsonArrayString);
                    JSONArray musicJsonArray = new JSONArray(musicJsonArrayString);
                    Log.d(PlayerService.TAG, "Load array: " + musicJsonArray.toString());
                    for (int i = 0; i < musicJsonArray.length(); i++) {
                        JSONObject musicJsonObject = (JSONObject) musicJsonArray.get(i);
                        MusicList.add(new Music((String) musicJsonObject.get("title"),
                                decodeBitmapBase64((String) musicJsonObject.get("cover")),
                                (String) musicJsonObject.get("ytUrl")));
                    }
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    public static Bitmap decodeBitmapBase64(String input) {
        byte[] decodedByte = Base64.decode(input, 0);
        return BitmapFactory.decodeByteArray(decodedByte, 0, decodedByte.length);
    }


}
