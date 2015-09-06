package com.soundtape;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.concurrent.Semaphore;

/**
 * Created by André on 02/09/2015.
 */
public class RequestPlaylistTask extends Thread {
    private final String playlistId;

    private ArrayList<String> videoIds = new ArrayList<String>();

    private final Semaphore hasFinished = new Semaphore(0);
    private boolean hadSuccess = false;
    private boolean needsUpdate = false;

    private String message;

    public RequestPlaylistTask(String playlistId) {
        this.playlistId = playlistId;
    }

    @Override
    public void run() {
        String requestUrl = "http://176.111.109.23:9194/playlist_id/" + playlistId;
        HttpClient httpClient = new DefaultHttpClient();
        HttpGet httpGet = new HttpGet(requestUrl);
        Logger.i(ScumTubeApplication.TAG, "Requesting playlist " + requestUrl);
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
                        Logger.w(ScumTubeApplication.TAG, "Download task interrupted");
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
                        Logger.i(ScumTubeApplication.TAG, "ready");
                        final JSONArray videoIdsJsonArray = jsonObject.getJSONArray("ids");
                        if (videoIdsJsonArray != null) {
                            for (int i=0;i<videoIdsJsonArray.length();i++){
                                videoIds.add(videoIdsJsonArray.get(i).toString());
                            }
                        }
                        message = "";
                        hadSuccess = true;
                        hasFinished.release();
                        return;
                    } else if (jsonObject.has("error")) {
                        final String errorMsg = jsonObject.getString("error");
                        throw new Exception(errorMsg);
                    }
                    Logger.i(ScumTubeApplication.TAG, jsonObject.getString("scheduled"));
                    Thread.sleep(1000);
                }
            }
            if (this.isInterrupted()) {
                Logger.w(ScumTubeApplication.TAG, "Requesting playlist task interrupted.");
                hasFinished.release();
                return;
            }
        } catch (InterruptedException e) {
            Logger.w(ScumTubeApplication.TAG, "Requesting playlist interrupted.");
            hasFinished.release();
            return;
        } catch (Exception e) {
            e.printStackTrace();
            Logger.e(ScumTubeApplication.TAG, e.getClass().getName(), e);
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

    public ArrayList<String> getVideoIds() {
        return videoIds;
    }
}
