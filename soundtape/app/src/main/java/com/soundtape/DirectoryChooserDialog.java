package com.soundtape;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.os.Environment;
import android.text.Editable;
import android.view.ContextThemeWrapper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Created by André on 13/09/2015.
 */
public class DirectoryChooserDialog {
    private boolean m_isNewFolderEnabled = true;
    private String m_sdcardDirectory = "";
    private Context m_context;
    private TextView m_titleView;

    private String m_dir = "";
    private List<String> m_subdirs = null;
    private ChosenDirectoryListener m_chosenDirectoryListener = null;
    private ArrayAdapter<String> m_listAdapter = null;

    //////////////////////////////////////////////////////
    // Callback interface for selected directory
    //////////////////////////////////////////////////////
    public interface ChosenDirectoryListener {
        public void onChosenDir(String chosenDir);
    }

    public DirectoryChooserDialog(Context context, ChosenDirectoryListener chosenDirectoryListener) {
        m_context = context;
        m_sdcardDirectory = Environment.getExternalStorageDirectory().getAbsolutePath();
        m_chosenDirectoryListener = chosenDirectoryListener;

        try {
            m_sdcardDirectory = new File(m_sdcardDirectory).getCanonicalPath();
        } catch (IOException ioe) {
        }
    }

    ///////////////////////////////////////////////////////////////////////
    // setNewFolderEnabled() - enable/disable new folder button
    ///////////////////////////////////////////////////////////////////////

    public void setNewFolderEnabled(boolean isNewFolderEnabled) {
        m_isNewFolderEnabled = isNewFolderEnabled;
    }

    public boolean getNewFolderEnabled() {
        return m_isNewFolderEnabled;
    }

    ///////////////////////////////////////////////////////////////////////
    // chooseDirectory() - load directory chooser dialog for initial
    // default sdcard directory
    ///////////////////////////////////////////////////////////////////////

    public void chooseDirectory() {
        // Initial directory is sdcard directory
        chooseDirectory(m_sdcardDirectory);
    }

    ////////////////////////////////////////////////////////////////////////////////
    // chooseDirectory(String dir) - load directory chooser dialog for initial
    // input 'dir' directory
    ////////////////////////////////////////////////////////////////////////////////

    public void chooseDirectory(String dir) {
        File dirFile = new File(dir);
        if (!dirFile.exists() || !dirFile.isDirectory()) {
            dir = m_sdcardDirectory;
        }

        try {
            dir = new File(dir).getCanonicalPath();
        } catch (IOException ioe) {
            return;
        }

        m_dir = dir;
        m_subdirs = getDirectories(dir);

        class DirectoryOnClickListener implements DialogInterface.OnClickListener {
            public void onClick(DialogInterface dialog, int item) {
                // Navigate into the sub-directory
                m_dir += "/" + ((AlertDialog) dialog).getListView().getAdapter().getItem(item);
                updateDirectory();
            }
        }

        AlertDialog.Builder dialogBuilder =
                createDirectoryChooserDialog(dir, m_subdirs, new DirectoryOnClickListener());

        dialogBuilder.setPositiveButton("OK", new OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                // Current directory chosen
                if (m_chosenDirectoryListener != null) {
                    // Call registered listener supplied with the chosen directory
                    m_chosenDirectoryListener.onChosenDir(m_dir);
                }
            }
        }).setNegativeButton("Cancel", null);

        final AlertDialog dirsDialog = dialogBuilder.create();

        // Show directory chooser dialog
        dirsDialog.show();
    }

    private boolean createSubDir(String newDir) {
        File newDirFile = new File(newDir);
        if (!newDirFile.exists()) {
            return newDirFile.mkdir();
        }

        return false;
    }

    private List<String> getDirectories(String dir) {
        List<String> dirs = new ArrayList<String>();

        try {
            File dirFile = new File(dir);
            if (!dirFile.exists() || !dirFile.isDirectory()) {
                return dirs;
            }

            for (File file : dirFile.listFiles()) {
                if (file.isDirectory()) {
                    dirs.add(file.getName());
                }
            }
        } catch (Exception e) {
        }

        Collections.sort(dirs, new Comparator<String>() {
            public int compare(String o1, String o2) {
                return o1.compareTo(o2);
            }
        });

        return dirs;
    }

    private AlertDialog.Builder createDirectoryChooserDialog(String title, List<String> listItems,
                                                             DialogInterface.OnClickListener onClickListener) {
        AlertDialog.Builder dialogBuilder = new AlertDialog.Builder(new ContextThemeWrapper(m_context, android.R.style.Theme_Holo_Dialog));

        // Create custom view for AlertDialog title containing
        // current directory TextView and possible 'New folder' button.
        // Current directory TextView allows long directory path to be wrapped to multiple lines.
        RelativeLayout titleLayout = new RelativeLayout(m_context);

        m_titleView = new TextView(m_context);
        RelativeLayout.LayoutParams titleViewLp = new RelativeLayout.LayoutParams(LayoutParams.FILL_PARENT, LayoutParams.WRAP_CONTENT);
        titleViewLp.addRule(RelativeLayout.ALIGN_PARENT_TOP);
        m_titleView.setId(1);
        m_titleView.setLayoutParams(titleViewLp);
        m_titleView.setTextAppearance(m_context, android.R.style.TextAppearance_Large);
        m_titleView.setTextColor(m_context.getResources().getColor(android.R.color.white));
        m_titleView.setGravity(Gravity.CENTER_VERTICAL | Gravity.CENTER_HORIZONTAL);
        m_titleView.setText(title);

        Button newUpButton = new Button(m_context);
        RelativeLayout.LayoutParams newUpButtonLp = new RelativeLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        newUpButtonLp.addRule(RelativeLayout.BELOW, m_titleView.getId());
        newUpButton.setId(2);
        newUpButton.setLayoutParams(newUpButtonLp);
        newUpButton.setTextColor(m_context.getResources().getColor(android.R.color.white));
        newUpButton.setText("Up");
        newUpButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (m_dir.equals(m_sdcardDirectory)) {
                    // The very top level directory, do nothing
                } else {
                    // Navigate back to an upper directory
                    m_dir = new File(m_dir).getParent();
                    updateDirectory();
                }
            }
        });

        Button newDirButton = new Button(m_context);
        RelativeLayout.LayoutParams newDirButtonLp = new RelativeLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        newDirButtonLp.addRule(RelativeLayout.BELOW, m_titleView.getId());
        newDirButtonLp.addRule(RelativeLayout.RIGHT_OF, newUpButton.getId());
        newDirButton.setId(3);
        newDirButton.setLayoutParams(newDirButtonLp);
        newDirButton.setTextColor(m_context.getResources().getColor(android.R.color.white));
        newDirButton.setText("New folder");
        newDirButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                final EditText input = new EditText(m_context);
                input.setTextColor(m_context.getResources().getColor(android.R.color.white));

                // Show new folder name input dialog
                new AlertDialog.Builder(new ContextThemeWrapper(m_context, android.R.style.Theme_Holo_Dialog)).
                        setTitle("New folder name").
                        setView(input).setPositiveButton("OK", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int whichButton) {
                        Editable newDir = input.getText();
                        String newDirName = newDir.toString();
                        // Create new directory
                        if (createSubDir(m_dir + "/" + newDirName)) {
                            // Navigate into the new directory
                            m_dir += "/" + newDirName;
                            updateDirectory();
                        } else {
                            Toast.makeText(
                                    m_context, "Failed to create '" + newDirName +
                                            "' folder", Toast.LENGTH_SHORT).show();
                        }
                    }
                }).setNegativeButton("Cancel", null).show();
            }
        });

        if (!m_isNewFolderEnabled) {
            newDirButton.setVisibility(View.GONE);
        }

        titleLayout.addView(m_titleView);
        titleLayout.addView(newUpButton);
        titleLayout.addView(newDirButton);

        dialogBuilder.setCustomTitle(titleLayout);

        m_listAdapter = createListAdapter(listItems);

        dialogBuilder.setSingleChoiceItems(m_listAdapter, -1, onClickListener);

        return dialogBuilder;
    }

    private void updateDirectory() {
        m_subdirs.clear();
        m_subdirs.addAll(getDirectories(m_dir));
        m_titleView.setText(m_dir);

        m_listAdapter.notifyDataSetChanged();
    }

    private ArrayAdapter<String> createListAdapter(List<String> items) {
        return new ArrayAdapter<String>(m_context,
                android.R.layout.select_dialog_item, android.R.id.text1, items) {
            @Override
            public View getView(int position, View convertView,
                                ViewGroup parent) {
                View v = super.getView(position, convertView, parent);

                if (v instanceof TextView) {
                    // Enable list item (directory) text wrapping
                    TextView tv = (TextView) v;
                    tv.setTextColor(m_context.getResources().getColor(android.R.color.white));
                    tv.getLayoutParams().height = LayoutParams.WRAP_CONTENT;
                    tv.setEllipsize(null);
                }
                return v;
            }
        };
    }
}