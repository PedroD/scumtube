package com.scumtube;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
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

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;

public class HistoryActivity extends AbstractActivity {

    private static MusicArrayAdapter adapter;
    private static ListView listView;
    public static boolean hasView = false;
    private static ArrayList<Music> musicArrayList;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);


    }

    @Override
    public void onNewIntent(Intent intent){
        super.onNewIntent(intent);
        if (intent.getExtras() != null) {
            final Bundle extras = intent.getExtras();
            final String extraText = extras.getString(Intent.EXTRA_TEXT);
            if (extraText != null && extraText.equals(PlayerService.EXTRA_DATASETCHANGED)){
                Log.i(Core.TAG, "Intent");
                adapter.notifyDataSetChanged();
            }
        } else {
            Log.i(Core.TAG, "getView");
            getView();
        }
    }


    public void getView(){
        loadMusicList();

        createAdapter();

        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {

            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                final Intent playerService = new Intent(HistoryActivity.this, PlayerService.class);
                playerService.putExtra("ytUrl", musicArrayList.get(position).getYtUrl());
                startService(playerService);
                sendHomeIntent();
            }
        });

        hasView = true;
    }

    public void createAdapter(){
        setContentView(R.layout.activity_history);

        listView = (ListView) findViewById(R.id.history_listview);
        listView.setEmptyView((View) findViewById(R.id.history_empty));
        musicArrayList = MusicList.getMusicArrayList();

        adapter = new MusicArrayAdapter(this,
                android.R.layout.simple_list_item_1, musicArrayList);
        listView.setAdapter(adapter);
    }

    @Override
    public void onResume(){
        super.onResume();
        Log.i(Core.TAG, "onResume");
        if(!hasView){
            getView();
        }
    }

    @Override
    public void onPause(){
        super.onPause();
        hasView = false;
    }

    @Override
    public void finish(){
        super.finish();
        hasView = false;
    }


    public void sendHomeIntent(){
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


    public void loadMusicList() {
        if (MusicList.getMusicArrayList().isEmpty()) {
            SharedPreferences preferences = getSharedPreferences(PlayerService.PREFS_NAME, Context.MODE_PRIVATE);
            String musicJsonArrayString = preferences.getString(PlayerService.PREFS_MUSICLIST, null);
            if (musicJsonArrayString != null) {
                try {
                    JSONArray musicJsonArray = new JSONArray(musicJsonArrayString);
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
