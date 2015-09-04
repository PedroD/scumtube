package com.scumtube;

import java.util.ArrayList;
import java.util.Random;

/**
 * Created by André on 02/09/2015.
 */
public class Playlist {
    private ArrayList<String> videoIds;
    private int currentMusic = 0;

    public Playlist(ArrayList<String> videoIds){
        this.videoIds = videoIds;
    }

    public String getCurrentMusic() {
        int previous = getPreviousMusicIndex();
        int next = getNextMusicIndex();
        new RequestMp3Task(ScumTubeApplication.parseVideoId(videoIds.get(previous))).start();
        new RequestMp3Task(ScumTubeApplication.parseVideoId(videoIds.get(next))).start();
        return videoIds.get(currentMusic);
    }

    public void changeToNextMusic(){
        currentMusic = getNextMusicIndex();
    }

    public void changeToPreviousMusic(){
        currentMusic = getPreviousMusicIndex();
    }

    private int getPreviousMusicIndex() {
        if(isFirstMusic()){
            return videoIds.size() - 1;
        } else {
            return (currentMusic - 1) % videoIds.size();
        }
    }

    private int getNextMusicIndex() {
        return (currentMusic + 1) % videoIds.size();
    }

    public boolean isFirstMusic(){
        return currentMusic == 0;
    }

    public boolean isLastMusic(){
        return currentMusic == (videoIds.size() - 1);
    }

    public void shuffle(){
        Random rand = new Random();
        currentMusic = rand.nextInt(videoIds.size());
    }


}
