package com.backyt;

import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v7.app.ActionBarActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.concurrent.ExecutionException;


public class PlayerActivity extends ActionBarActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getIntent().getExtras() != null){
            Bundle extras = getIntent().getExtras();
            String ytLink = extras.getString(Intent.EXTRA_TEXT);
            String streamUrl = null;
            try {
                streamUrl = new RequestMp3().execute(parseVideoId(ytLink)).get();
            } catch (InterruptedException e) {
                e.printStackTrace();
            } catch (ExecutionException e) {
                e.printStackTrace();
            }
            Log.i("Youtube", ytLink);
            Log.i("VAGINA", PlayerService.class.getName());
            Intent playerService = new  Intent(this, PlayerService.class);
            playerService.setAction(PlayerService.ACTION_PLAY);
            playerService.putExtra("streamUrl", streamUrl);
            startService(playerService);

        }
        finish();
    }

    private String parseVideoId(String url){
        String [] splittedUrl = url.split("/");
        return splittedUrl[3]; //splittedUrl[3] is the id of the video
    }

    class RequestMp3 extends AsyncTask<String, Void, String> {

        @Override
        protected String doInBackground(String... videoId) {
            String requestUrl = "http://wavedomotics.com:9194/video_id/" + videoId[0];
            HttpClient httpClient = new DefaultHttpClient();
            HttpGet httpGet = new HttpGet(requestUrl);
            try{
                JSONObject jsonObject;
                while(true){
                    HttpResponse httpResponse = httpClient.execute(httpGet);
                    HttpEntity httpEntity = httpResponse.getEntity();
                    if(httpEntity != null) {
                        InputStream inputStream = httpEntity.getContent();

                        BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream));
                        StringBuilder stringBuilder = new StringBuilder();

                        String line = bufferedReader.readLine();
                        while (line != null) {
                            stringBuilder.append(line + " \n");
                            line = bufferedReader.readLine();
                        }
                        bufferedReader.close();

                        jsonObject = new JSONObject(stringBuilder.toString());
                        if(jsonObject.has("ready")){
                            break;
                        }
                        Log.i("Pedido", jsonObject.getString("scheduled"));
                        Thread.sleep(5000);
                    }
                }
                Log.i("Pedido", jsonObject.getString("url"));
                return jsonObject.getString("url");

        } catch (IOException e){
        } catch (JSONException e){
        } catch (InterruptedException e) {
            }
            return "";
        }
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_player, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }
}
