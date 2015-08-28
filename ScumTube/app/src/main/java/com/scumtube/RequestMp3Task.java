package com.scumtube;

import android.util.Log;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.concurrent.Semaphore;

/**
 * Created by André on 27/08/2015.
 */
public class RequestMp3Task extends Thread {

    private final String videoId;

    private String mp3Url;
    private String coverUrl;
    private String title;

    private final Semaphore hasFinished = new Semaphore(0);
    private boolean hadSuccess = false;
    private boolean needsUpdate = false;

    private String message;

    public RequestMp3Task(String videoId) {
        this.videoId = videoId;
    }

    @Override
    public void run() {
        String requestUrl = "http://176.111.109.23:9194/video_id/" + videoId;
        HttpClient httpClient = new DefaultHttpClient();
        HttpGet httpGet = new HttpGet(requestUrl);
        Log.i(ScumTubeApplication.TAG, "Requesting music " + requestUrl);
        try {
            JSONObject jsonObject;
            while (!this.isInterrupted()) {
                HttpResponse httpResponse = httpClient.execute(httpGet);
                HttpEntity httpEntity = httpResponse.getEntity();
                if (httpEntity != null) {
                    InputStream inputStream = httpEntity.getContent();

                    BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream));
                    StringBuilder stringBuilder = new StringBuilder();

                    String line = bufferedReader.readLine();
                    while (line != null && !this.isInterrupted()) {
                        stringBuilder.append(line);
                        stringBuilder.append(" \n");
                        line = bufferedReader.readLine();
                    }
                    bufferedReader.close();

                    if (this.isInterrupted()) {
                        Log.w(ScumTubeApplication.TAG, "Download task interrupted");
                        return;
                    }

                    jsonObject = new JSONObject(stringBuilder.toString());
                        /*
                         * Validate app version.
                         */
                    if (jsonObject.has("version")) {
                        final String v = jsonObject.getString("version");
                        final String d = ScumTubeApplication.md5(v);
                        if (!ScumTubeApplication._T.equals(d)) {
                            message = "A new version of ScumTube was released! You need to update.";
                            needsUpdate = true;
                            hasFinished.release();
                            return;
                        }
                    }
                    if (jsonObject.has("ready")) {
                        mp3Url = jsonObject.getString("url");
                        coverUrl = jsonObject.getString("cover");
                        title = jsonObject.getString("title");
                        Log.i(ScumTubeApplication.TAG, title + " :: " + mp3Url + " :: " + coverUrl);

                        message = "";
                        hadSuccess = true;
                        hasFinished.release();
                        return;
                    } else if (jsonObject.has("error")) {
                        final String errorMsg = jsonObject.getString("error");
                        throw new Exception(errorMsg);
                    }
                    Log.i(ScumTubeApplication.TAG, jsonObject.getString("scheduled"));
                    Thread.sleep(2000);
                }
            }
            if (this.isInterrupted()) {
                Log.w(ScumTubeApplication.TAG, "Download task interrupted.");
                hasFinished.release();
                return;
            }
        } catch (InterruptedException e) {
            Log.w(ScumTubeApplication.TAG, "Download task interrupted.");
            hasFinished.release();
            return;
        } catch (Exception e) {
            e.printStackTrace();
            Log.e(ScumTubeApplication.TAG, e.getClass().getName(), e);
            message = "There was a problem contacting YouTube. Please check your Internet connection.";
            hasFinished.release();
            return;
        }
    }

    public void waitUntilFinished() throws InterruptedException {
        hasFinished.acquire();
    }

    public boolean hadSuccess() {
        return hadSuccess;
    }

    public boolean needsUpdate() {
        return needsUpdate;
    }

    public String getMessage() {
        return message;
    }

    public String getMp3Url() {
        return mp3Url;
    }

    public String getCoverUrl() {
        return coverUrl;
    }

    public String getTitle() {
        return title;
    }
}
