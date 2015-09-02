package com.scumtube;

import java.util.ArrayList;

/**
 * Created by André on 02/09/2015.
 */
public class Playlist {
    private ArrayList<String> videoIds;
    private int currentMusic;

    public Playlist(ArrayList<String> videoIds){
        this.videoIds = videoIds;
    }

    public void setCurrentMusic(int currentMusic) {
        this.currentMusic = currentMusic;
    }

    public String getCurrentMusic() {
        return videoIds.get(currentMusic);
    }

    public String getNextMusic(){
        currentMusic = (currentMusic + 1) % videoIds.size();
        return videoIds.get(currentMusic);
    }

    public String getPreviousMusic(){
        currentMusic = (currentMusic - 1) % videoIds.size();
        return videoIds.get(currentMusic);
    }


}
