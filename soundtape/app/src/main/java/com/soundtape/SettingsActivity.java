package com.soundtape;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;

import java.util.ArrayList;

public class SettingsActivity extends AbstractActivity {

    private final String DOWNLOAD_DESTINATION = "Change download destination";
    private ArrayList<String> settingsArrayList = new ArrayList();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        settingsArrayList.add(DOWNLOAD_DESTINATION);

        ListView listView = (ListView) findViewById(R.id.settings_listview);
        SettingsArrayAdapter adapter = new SettingsArrayAdapter(this,
                android.R.layout.simple_list_item_1, settingsArrayList);

        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {

            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                switch (settingsArrayList.get(position)) {
                    case DOWNLOAD_DESTINATION:
                        // Create DirectoryChooserDialog and register a callback
                        DirectoryChooserDialog directoryChooserDialog =
                                new DirectoryChooserDialog(SettingsActivity.this,
                                        new DirectoryChooserDialog.ChosenDirectoryListener()
                                        {
                                            @Override
                                            public void onChosenDir(String chosenDir)
                                            {
                                                SoundtapeApplication.downloadDirectory = chosenDir;
                                                saveDownloadDirectory();
                                            }
                                        });
                        // Toggle new folder button enabling
                        directoryChooserDialog.setNewFolderEnabled(true);
                        // Load directory chooser dialog for initial 'm_chosenDir' directory.
                        // The registered callback will be called upon final directory selection.
                        directoryChooserDialog.chooseDirectory(SoundtapeApplication.downloadDirectory);
                }
            }
        });

        listView.setAdapter(adapter);
    }

    private void saveDownloadDirectory() {
        SharedPreferences preferences = getSharedPreferences(SoundtapeApplication.PREFS_NAME, 0);
        SharedPreferences.Editor editor = preferences.edit();
        editor.putString(SoundtapeApplication.PREFS_DOWNLOAD_DIRECTORY, SoundtapeApplication.downloadDirectory);
        editor.commit();
    }

    private class SettingsArrayAdapter extends ArrayAdapter<String> {


        public SettingsArrayAdapter(Context context, int textViewResourceId, ArrayList<String> objects) {
            super(context, textViewResourceId, objects);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View v = convertView;
            ViewHolder holder;
            if (v == null) {

                LayoutInflater vi = LayoutInflater.from(getContext());
                v = vi.inflate(R.layout.settings_item, parent, false);

                holder = new ViewHolder();

                holder.setting = (TextView) v.findViewById(R.id.settings_item_setting);

                v.setTag(holder);

            } else {
                holder = (ViewHolder) v.getTag();
            }

            String item = getItem(position);
            holder.setting.setText(item);
            return v;
        }
    }

    private static class ViewHolder {
        TextView setting;
    }

}
