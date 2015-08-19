package com.scumtube;

import android.graphics.Bitmap;

import java.io.Serializable;

/**
 * Created by André on 07/08/2015.
 */
public class Music implements Serializable{
    private String title;
    private Bitmap cover;
    private String ytUrl;

    public Music(String title, Bitmap cover, String ytUrl){
        this.title = title;
        this.cover = cover;
        this.ytUrl = ytUrl;
    }

    public Music(String title, String ytUrl){
        this.title = title;
        this.ytUrl = ytUrl;
    }

    public String getYtUrl() {
        return ytUrl;
    }

    public String getTitle() {
        return title;
    }

    public Bitmap getCover() {
        return cover;
    }
}
