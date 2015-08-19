package com.scumtube;

import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Base64;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;

/**
 * Created by André on 07/08/2015.
 */
public class MusicList {

    private static MusicList singleton = new MusicList();

    private static ArrayList<Music> musicArrayList = new ArrayList<Music>();

    private MusicList() {

    }

    public static ArrayList<Music> getMusicArrayList() {
        return musicArrayList;
    }

    public static void setMusicArrayList(ArrayList<Music> musicArrayList){
        singleton.musicArrayList = musicArrayList;
    }

    public static void addFirst(Music music){
        remove(music);
        musicArrayList.add(0, music);
    }

    public static void add(Music music){
        remove(music);
        musicArrayList.add(music);
    }

    public static void remove(Music music) {
        for (int i = 0; i < musicArrayList.size(); i++) {
            if (musicArrayList.get(i).getYtUrl().equals(music.getYtUrl())) {
                musicArrayList.remove(i);
                break;
            }
        }
    }

    public static void saveMusicList(SharedPreferences sharedPreferences) {
        SharedPreferences preferences = sharedPreferences;
        SharedPreferences.Editor editor = preferences.edit();
        ArrayList<Music> a = MusicList.getMusicArrayList();
        JSONArray musicJsonArray = new JSONArray();
        for (Music m : a) {
            JSONObject musicJsonObject = new JSONObject();
            try {
                musicJsonObject.put("title", m.getTitle());
                musicJsonObject.put("cover", encodeBitmapTobase64(m.getCover()));
                musicJsonObject.put("ytUrl", m.getYtUrl());
                musicJsonArray.put(musicJsonObject);
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
        editor.putString(ScumTubeApplication.PREFS_MUSICLIST, musicJsonArray.toString());
        editor.commit();
    }

    public static String encodeBitmapTobase64(Bitmap image) {
        Bitmap imagex = image;
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        imagex.compress(Bitmap.CompressFormat.PNG, 100, baos);
        byte[] b = baos.toByteArray();
        String imageEncoded = Base64.encodeToString(b, Base64.DEFAULT);
        return imageEncoded;
    }

    public static void loadMusicList(SharedPreferences sharedPreferences) {
        if (MusicList.getMusicArrayList().isEmpty()) {
            SharedPreferences preferences = sharedPreferences;
            String musicJsonArrayString = preferences.getString(ScumTubeApplication.PREFS_MUSICLIST, null);
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
