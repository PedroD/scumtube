package com.scumtube;

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



}
