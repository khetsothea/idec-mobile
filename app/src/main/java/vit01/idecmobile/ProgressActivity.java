/*
 * Copyright (c) 2016-2017 Viktor Fedenyov <me@ii-net.tk> <https://ii-net.tk>
 *
 * This file is part of IDEC Mobile.
 *
 * IDEC Mobile is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * IDEC Mobile is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with IDEC Mobile.  If not, see <http://www.gnu.org/licenses/>.
 */

package vit01.idecmobile;

import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Bundle;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.mikepenz.google_material_typeface_library.GoogleMaterial;
import com.mikepenz.iconics.IconicsDrawable;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Hashtable;

import vit01.idecmobile.Core.Fetcher;
import vit01.idecmobile.Core.GlobalTransport;
import vit01.idecmobile.Core.Network;
import vit01.idecmobile.Core.Sender;
import vit01.idecmobile.Core.SimpleFunctions;
import vit01.idecmobile.Core.Station;
import vit01.idecmobile.GUI.Reading.EchoReaderActivity;
import vit01.idecmobile.prefs.Config;

public class ProgressActivity extends AppCompatActivity {
    TextView info;
    ProgressBar progressBar;
    ImageView errorView;
    Button viewLog;
    String debugLog = "";
    boolean closeWindow = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        setTheme(Config.appTheme);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_progress);

        initUI();

        Intent gotIntent = getIntent();
        String task = gotIntent.getStringExtra("task");
        chooseTitle(task);

        SimpleFunctions.debugTaskFinished = false;
        SimpleFunctions.debugMessages.clear();
        new Thread(new updateDebug()).start();

        switch (task) {
            case "fetch":
                progressBar.setIndeterminate(true);
                new Thread(new doFetch()).start();
                break;
            case "send":
                progressBar.setIndeterminate(false);
                new Thread(new sendMessages()).start();
                break;
            case "upload_fp":
                progressBar.setIndeterminate(false);
                upload_fp uploader = new upload_fp();

                uploader.nodeindex = gotIntent.getIntExtra("nodeindex", Config.currentSelectedStation);
                uploader.filename = gotIntent.getStringExtra("filename");
                uploader.maxsize = gotIntent.getLongExtra("filesize", 0);
                uploader.description = gotIntent.getStringExtra("description");
                uploader.fecho = gotIntent.getStringExtra("fecho");
                uploader.input = gotIntent.getParcelableExtra("inputstream");

                file_upload_progress progress = new file_upload_progress();
                progress.loader = uploader;

                new Thread(uploader).start();
                new Thread(progress).start();

                break;
            case "download_fp":
                progressBar.setIndeterminate(false);

                break;
        }
    }

    @Override
    public void onBackPressed() {
        if (!SimpleFunctions.debugTaskFinished) {
            Toast.makeText(ProgressActivity.this, R.string.close_window_later, Toast.LENGTH_SHORT).show();
        } else if (!closeWindow) finish();
    }

    @Override
    public void onDestroy() {
        SimpleFunctions.debugTaskFinished = true;
        super.onDestroy();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        setContentView(R.layout.activity_progress);
        initUI();
        chooseTitle(getIntent().getStringExtra("task"));
        if (SimpleFunctions.debugTaskFinished && !closeWindow) {
            errorHappened();
        }
    }

    public void initUI() {
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        info = (TextView) findViewById(R.id.progress_status_text);
        progressBar = (ProgressBar) findViewById(R.id.progressbar);
        errorView = (ImageView) findViewById(R.id.error_view);
        viewLog = (Button) findViewById(R.id.progress_watch_log);

        viewLog.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                while (SimpleFunctions.debugMessages.size() > 0) {
                    debugLog += SimpleFunctions.debugMessages.remove() + "\n";
                }
                new AlertDialog.Builder(ProgressActivity.this)
                        .setMessage(debugLog)
                        .setTitle("Debug Log")
                        .setPositiveButton(android.R.string.ok, null)
                        .show();
            }
        });

        errorView.setImageDrawable(new IconicsDrawable(this, GoogleMaterial.Icon.gmd_warning).color(
                SimpleFunctions.colorFromTheme(this, android.R.attr.textColorSecondary)).sizeDp(100));
    }

    public void chooseTitle(String task) {
        String title;
        switch (task) {
            case "fetch":
                title = getString(R.string.loading_messages);
                break;
            case "send":
                title = getString(R.string.sending_letters);
                break;
            default:
                title = "<null>";
                break;
        }
        SimpleFunctions.setActivityTitle(this, title);
    }

    public void finishTask() {
        SimpleFunctions.debugTaskFinished = true;

        SimpleFunctions.prettyDebugMessages.clear();
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (closeWindow) finish();
            }
        });
    }

    public void errorHappened() {
        SimpleFunctions.debugTaskFinished = true;
        info.setText(R.string.done_with_errors);
        viewLog.setVisibility(View.VISIBLE);
        progressBar.setVisibility(View.GONE);
        errorView.setVisibility(View.VISIBLE);
        closeWindow = false;
    }

    private class updateDebug implements Runnable {
        @Override
        public void run() {
            while (!SimpleFunctions.debugTaskFinished) {
                try {
                    Thread.sleep(50);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (SimpleFunctions.debugMessages.size() > 0) {
                            String message = SimpleFunctions.debugMessages.remove() + "\n";
                            debugLog += message;
                        }

                        if (SimpleFunctions.prettyDebugMessages.size() > 0) {
                            info.setText(SimpleFunctions.prettyDebugMessages.remove());
                        }
                    }
                });
            }
        }
    }

    private class doFetch implements Runnable {
        @Override
        public void run() {
            Context appContext = getApplicationContext();

            ArrayList<String> fetched;
            int fetchedCount = 0;
            int error_flag = 0;

            try {
                Fetcher fetcher = new Fetcher(appContext, GlobalTransport.transport);

                for (Station station : Config.values.stations) {
                    if (!station.fetch_enabled) {
                        SimpleFunctions.debug("skip fetching " + station.nodename);
                        continue;
                    }

                    String xc_id = (station.xc_enable) ?
                            station.outbox_storage_id : null;
                    Integer ue_limit = (station.advanced_ue) ? station.ue_limit : 0;

                    fetched = fetcher.fetch_messages(
                            station.address,
                            station.echoareas,
                            xc_id,
                            Config.values.oneRequestLimit,
                            ue_limit,
                            station.pervasive_ue,
                            station.cut_remote_index,
                            Config.values.connectionTimeout
                    );

                    if (fetched != null) fetchedCount += fetched.size();
                    else error_flag++;
                }
            } catch (Exception e) {
                e.printStackTrace();
                SimpleFunctions.debug(getString(R.string.error_formatted, e.toString()));
                error_flag++;
            } finally {
                final int finalFetched = fetchedCount;
                final int finalErrorFlag = error_flag;

                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        String message = "";
                        if (finalErrorFlag > 0) {
                            errorHappened();

                            message += getString(R.string.errors, finalErrorFlag) + "\n\n";

                            if (finalFetched == 0)
                                message += getString(R.string.internet_error);
                        } else if (finalFetched == 0)
                            message += getString(R.string.no_new_messages);

                        if (!message.equals(""))
                            Toast.makeText(ProgressActivity.this, message, Toast.LENGTH_SHORT).show();

                        if (finalFetched > 0) {
                            Toast.makeText(getApplicationContext(), getString(R.string.messages_got, finalFetched), Toast.LENGTH_SHORT).show();

                            if (Config.values.openUnreadAfterFetch) {
                                Intent unreadIntent = new Intent(ProgressActivity.this, EchoReaderActivity.class);
                                unreadIntent.putExtra("echoarea", "_unread");
                                startActivity(unreadIntent);
                            }
                        }
                    }
                });
                finishTask();
            }
        }
    }

    private class sendMessages implements Runnable {
        @Override
        public void run() {
            int sent = 0;

            try {
                sent = Sender.sendMessages(getApplicationContext());
                SimpleFunctions.debug(getString(R.string.messages_sent, sent));
            } catch (Exception e) {
                e.printStackTrace();
                SimpleFunctions.debug(getString(R.string.error_formatted, e.toString()));

                errorHappened();
            } finally {
                final int finalSent = sent;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(getApplicationContext(), getString(R.string.messages_sent, finalSent), Toast.LENGTH_SHORT).show();
                    }
                });

                finishTask();
            }
        }
    }

    class file_download implements Runnable {
        long maxsize = 0;
        long bytesdone = 0;
        String maxsizestr = "";
        Context context;

        @Override
        public void run() {
            try {
                // do job
                context = ProgressActivity.this;
                maxsizestr = android.text.format.Formatter.formatFileSize(context, maxsize);
            } catch (Exception e) {
                e.printStackTrace();
                SimpleFunctions.debug(getString(R.string.error_formatted, e.toString()));

                errorHappened();
            } finally {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        //job result
                    }
                });

                finishTask();
            }
        }
    }

    private class file_upload_progress implements Runnable {
        upload_fp loader;

        public void run() {
            while (loader != null && !SimpleFunctions.debugTaskFinished) {
                if (loader.maxsize > 0) {

                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            float percentage_temp = (loader.bytesdone * 100) / loader.maxsize;
                            int percentage = (int) percentage_temp;
                            final String statusString = getString(R.string.upload_in_process,
                                    android.text.format.Formatter.formatFileSize(loader.context,
                                            loader.bytesdone), loader.maxsizestr);

                            progressBar.setProgress(percentage);
                            SimpleFunctions.setActivityTitle(ProgressActivity.this,
                                    getString(R.string.upload_percentage, percentage));

                            SimpleFunctions.pretty_debug(statusString);
                        }
                    });
                }

                try {
                    Thread.sleep(300);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    public class upload_fp implements Runnable {
        public long bytesdone = 0;
        int nodeindex = 0;
        long maxsize = 0;
        String maxsizestr = "";
        Context context;

        String filename, description, fecho;
        Uri input;

        @Override
        public void run() {
            String responseString = getString(R.string.error);

            try {
                context = ProgressActivity.this;
                maxsizestr = android.text.format.Formatter.formatFileSize(context, maxsize);

                InputStream fis = context.getContentResolver().openInputStream(input);

                Station station = Config.values.stations.get(nodeindex);
                Hashtable<String, String> formdata = new Hashtable<>();
                formdata.put("fecho", fecho);
                formdata.put("filename", filename);
                formdata.put("pauth", station.authstr);
                formdata.put("dsc", description);

                InputStream uploadResults = Network.performFileUpload(this, station.address + "f/p", fis,
                        formdata, "file", filename, Config.values.connectionTimeout);

                String readableResult = SimpleFunctions.readIt(uploadResults);

                if (readableResult.startsWith("file ok:")) {
                    responseString = getString(R.string.file_upload_success);
                } else {
                    responseString = readableResult;
                }

            } catch (Exception e) {
                e.printStackTrace();
                SimpleFunctions.debug(getString(R.string.error_formatted, e.toString()));

                errorHappened();
            } finally {
                final String finalResponseString = responseString;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(context, finalResponseString, Toast.LENGTH_LONG).show();
                    }
                });

                finishTask();
            }
        }
    }
}