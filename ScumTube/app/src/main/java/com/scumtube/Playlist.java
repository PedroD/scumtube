package com.scumtube;

import java.util.ArrayList;
import java.util.Random;

/**
 * Created by André on 02/09/2015.
 */
public class Playlist {
    private ArrayList<String> videoIds;
    private int currentMusic = 0;
    private int randomNext = 0;
    private boolean shuffle = false;

    public Playlist(ArrayList<String> videoIds) {
        this.videoIds = videoIds;
    }

    public String getCurrentMusicId() {
        return videoIds.get(currentMusic);
    }

    public void changeToNextMusic() {
        if (!this.shuffle)
            currentMusic = getNextMusicIndex();
        else
            shuffle();
    }

    public void changeToPreviousMusic() {
        if (!this.shuffle)
            currentMusic = getPreviousMusicIndex();
        else
            shuffle();
    }

    private int getPreviousMusicIndex() {
        if (isFirstMusic()) {
            return videoIds.size() - 1;
        } else {
            return (currentMusic - 1) % videoIds.size();
        }
    }

    private int getNextMusicIndex() {
        return (currentMusic + 1) % videoIds.size();
    }

    public boolean isFirstMusic() {
        return currentMusic == 0;
    }

    public boolean isLastMusic() {
        return currentMusic == (videoIds.size() - 1);
    }

    private void shuffle() {
        Random rand = new Random();
        int i;
        do {
            i = rand.nextInt(videoIds.size());
        } while (i == randomNext);
        currentMusic = randomNext;
        randomNext = i;
    }

    public void setShuffle(boolean enabled) {
        if (!this.shuffle && enabled)
            shuffle();
        this.shuffle = enabled;
    }

    public String getPreviousMusicId() {
        if (!this.shuffle)
            return videoIds.get(getPreviousMusicIndex());
        else
            return videoIds.get(randomNext);
    }

    public String getNextMusicId() {
        if (!this.shuffle)
            return videoIds.get(getNextMusicIndex());
        else
            return videoIds.get(randomNext);
    }


}
