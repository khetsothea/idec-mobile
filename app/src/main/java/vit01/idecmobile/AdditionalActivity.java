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

import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.support.design.widget.TabLayout;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.view.ViewPager;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.text.format.Formatter;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.SimpleAdapter;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import vit01.idecmobile.Core.AbstractTransport;
import vit01.idecmobile.Core.Blacklist;
import vit01.idecmobile.Core.ExternalStorage;
import vit01.idecmobile.Core.Fetcher;
import vit01.idecmobile.Core.GlobalConfig;
import vit01.idecmobile.Core.GlobalTransport;
import vit01.idecmobile.Core.IDECFunctions;
import vit01.idecmobile.Core.Network;
import vit01.idecmobile.Core.SimpleFunctions;
import vit01.idecmobile.Core.Station;
import vit01.idecmobile.prefs.Config;

public class AdditionalActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        setTheme(Config.appTheme);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_additional);

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        SimpleFunctions.setDisplayHomeAsUpEnabled(this);
        SimpleFunctions.setActivityTitle(this, getString(R.string.additional));

        SectionsPagerAdapter mSectionsPagerAdapter = new SectionsPagerAdapter(getSupportFragmentManager());
        ViewPager mViewPager = (ViewPager) findViewById(R.id.container);
        mViewPager.setAdapter(mSectionsPagerAdapter);

        TabLayout tabLayout = (TabLayout) findViewById(R.id.tabs);
        tabLayout.setupWithViewPager(mViewPager);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode > 3) return;

        if (resultCode == RESULT_OK) {
            final File file = (File) data.getSerializableExtra("selected_file");
            Toast.makeText(AdditionalActivity.this, getString(R.string.file_chosen, file.getAbsolutePath()), Toast.LENGTH_SHORT).show();

            if (requestCode == 1) {
                Intent intent = new Intent(AdditionalActivity.this, DebugActivity.class);
                intent.putExtra("task", "import_blacklist");
                intent.putExtra("file", file);
                startActivity(intent);
            } else if (requestCode == 2) {
                Intent intent = new Intent(AdditionalActivity.this, DebugActivity.class);
                intent.putExtra("task", "import_bundle");
                intent.putExtra("file", file);
                startActivity(intent);
            } else if (requestCode == 3) {
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        String result;
                        if (!file.exists() || !file.canRead())
                            result = getString(R.string.no_file_warning);
                        else {
                            if (file.length() > (1024 * 1024))
                                result = getString(R.string.config_very_big);
                            else {
                                try {
                                    FileInputStream is = new FileInputStream(file);
                                    ObjectInputStream ois = new ObjectInputStream(is);
                                    Config.values = (GlobalConfig) ois.readObject();
                                    ois.close();
                                    is.close();
                                    Config.configUpdate(getApplicationContext());
                                    result = getString(R.string.done);
                                } catch (Exception e) {
                                    result = getString(R.string.config_not_found) + ": " + e.toString();
                                    SimpleFunctions.debug(result);
                                    e.printStackTrace();

                                    Config.loadConfig(getApplicationContext());
                                }
                                Config.writeConfig(getApplicationContext());
                            }
                        }
                        final String finalResult = result;
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                Toast.makeText(AdditionalActivity.this, finalResult, Toast.LENGTH_SHORT).show();
                            }
                        });
                    }
                }).start();
            }
        }
    }

    public static class xfile_Fragment extends Fragment {
        public xfile_Fragment() {
        }

        public static xfile_Fragment newInstance() {
            return new xfile_Fragment();
        }

        @Override
        public View onCreateView(final LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
            View rootView = inflater.inflate(R.layout.fragment_additional_xfile, container, false);

            final Spinner spinner = (Spinner) rootView.findViewById(R.id.additional_stations_spinner);

            ArrayAdapter<String> adapter = new ArrayAdapter<>(getContext(),
                    android.R.layout.simple_spinner_dropdown_item, IDECFunctions.getStationsNames());
            spinner.setAdapter(adapter);

            final ListView listview = (ListView) rootView.findViewById(R.id.additional_xfile_list);

            Button button = (Button) rootView.findViewById(R.id.additional_load_xfile_list);
            button.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Toast.makeText(getContext(), getString(R.string.wait), Toast.LENGTH_SHORT).show();
                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            Context mContext = getContext();

                            List<HashMap<String, String>> adapter_data = new ArrayList<>();
                            ArrayList<String> lines = Fetcher.xfile_list_download(mContext,
                                    Config.values.stations.get(spinner.getSelectedItemPosition()));

                            for (String line : lines) {
                                xfile_entry entry = new xfile_entry(line);

                                HashMap<String, String> entryMap = new HashMap<>(2);
                                entryMap.put("First Line", entry.filename);
                                entryMap.put("Second Line", Formatter.formatFileSize(mContext, entry.filesize) + " - " + entry.description);
                                adapter_data.add(entryMap);
                            }

                            final SimpleAdapter adapter = new SimpleAdapter(mContext, adapter_data,
                                    android.R.layout.simple_list_item_2,
                                    new String[]{"First Line", "Second Line"},
                                    new int[]{android.R.id.text1, android.R.id.text2});

                            getActivity().runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    listview.setAdapter(adapter);
                                }
                            });
                        }
                    }).start();
                }
            });

            listview.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                @Override
                public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                    TextView filename_view = (TextView) view.findViewById(android.R.id.text1);
                    String filename = filename_view.getText().toString();

                    Intent intent = new Intent(getActivity(), DebugActivity.class);
                    intent.putExtra("task", "download_file");
                    intent.putExtra("nodeindex", spinner.getSelectedItemPosition());
                    intent.putExtra("filename", filename);
                    startActivity(intent);
                }
            });

            return rootView;
        }

        public class xfile_entry {
            String filename = "null", description = "null";
            int filesize = 0;

            xfile_entry(String rawline) {
                String[] values = rawline.split(":");
                if (values.length == 3) {
                    filename = values[0];
                    filesize = Integer.parseInt(values[1]);
                    description = values[2];
                }
            }
        }
    }

    public static class Database_Fragment extends Fragment {
        AbstractTransport transport;
        Spinner echoareas_spinner;
        ArrayAdapter<String> spinner_adapter;
        ArrayList<String> echolist = new ArrayList<>();

        public Database_Fragment() {
        }

        public static Database_Fragment newInstance() {
            return new Database_Fragment();
        }

        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
            transport = GlobalTransport.transport;

            View rootView = inflater.inflate(R.layout.fragment_additional_database, container, false);

            Button delete_everything = (Button) rootView.findViewById(R.id.additional_database_clear_all);
            delete_everything.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    new AlertDialog.Builder(getContext())
                            .setTitle(R.string.action_delete_database)
                            .setMessage(R.string.are_you_crazy)
                            .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int which) {
                                    transport.FuckDeleteEverything();
                                    updateEchoList();
                                    Toast.makeText(getContext(), R.string.deleting_database_complete, Toast.LENGTH_SHORT).show();
                                }
                            })
                            .setNegativeButton(android.R.string.no, new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int which) {
                                    Toast.makeText(getContext(), R.string.ok_database_is_alive, Toast.LENGTH_SHORT).show();
                                }
                            })
                            .show();
                }
            });

            Button clear_xc_cache = (Button) rootView.findViewById(R.id.additional_clear_xc);
            clear_xc_cache.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    for (Station station : Config.values.stations) {
                        boolean deleted = SimpleFunctions.delete_xc_from_station(getContext(), station);
                        if (!deleted)
                            SimpleFunctions.debug(getString(R.string.station_deletion_error, station.nodename, station.outbox_storage_id));
                    }
                    Toast.makeText(getContext(), R.string.xc_cache_clear_complete, Toast.LENGTH_SHORT).show();
                }
            });

            final EditText truncate_echoarea = (EditText) rootView.findViewById(R.id.additional_truncate_echoarea_limit);
            truncate_echoarea.setText("50");

            echoareas_spinner = (Spinner) rootView.findViewById(R.id.additional_full_echolist);
            spinner_adapter = new ArrayAdapter<>(getContext(), android.R.layout.simple_spinner_dropdown_item, echolist);
            echoareas_spinner.setAdapter(spinner_adapter);
            updateEchoList();

            Button delete_echoarea = (Button) rootView.findViewById(R.id.additional_database_clear_echo);
            delete_echoarea.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    String current_echo = ((TextView) echoareas_spinner.getSelectedView()).getText().toString();

                    if (!current_echo.equals("")) {
                        transport.deleteEchoarea(current_echo, true);
                        Toast.makeText(getContext(), R.string.echo_deletion_complete, Toast.LENGTH_SHORT).show();
                        updateEchoList();
                    }
                }
            });

            Button export_echoarea = (Button) rootView.findViewById(R.id.additional_database_export_echoarea);
            export_echoarea.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    String current_echo = ((TextView) echoareas_spinner.getSelectedView()).getText().toString();

                    if (!current_echo.equals("")) {
                        String filename = current_echo + "_" + String.valueOf(System.currentTimeMillis()) + ".bundle";
                        ExternalStorage.initStorage();

                        File target = new File(ExternalStorage.rootStorage.getParentFile(), filename);

                        Intent intent = new Intent(getActivity(), DebugActivity.class);
                        intent.putExtra("task", "export_bundle");
                        intent.putExtra("file", target);

                        ArrayList<String> args = new ArrayList<>();
                        args.add(current_echo);

                        intent.putExtra("echoareas", args);
                        startActivity(intent);
                    }
                }
            });

            Button truncate_start = (Button) rootView.findViewById(R.id.additional_truncate_echo);
            truncate_start.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    int topLimit = Integer.parseInt(truncate_echoarea.getText().toString());
                    if (topLimit > 0) {
                        Intent intent = new Intent(getActivity(), DebugActivity.class);
                        intent.putExtra("task", "truncate_echo");
                        intent.putExtra("echoarea", ((TextView) echoareas_spinner.getSelectedView()).getText().toString());
                        intent.putExtra("limit", topLimit);
                        startActivity(intent);
                    } else
                        Toast.makeText(getContext(), R.string.wrong_input, Toast.LENGTH_SHORT).show();
                }
            });

            Button selectFile = (Button) rootView.findViewById(R.id.additional_select_file_bundle_import);
            selectFile.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Activity callingActivity = getActivity();
                    callingActivity.startActivityForResult(new Intent(callingActivity, FileChooserActivity.class), 2);
                }
            });

            Button exportAll = (Button) rootView.findViewById(R.id.additional_database_export_all);
            exportAll.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    ArrayList<String> echoareas = GlobalTransport.transport.fullEchoList();
                    if (echoareas.size() == 0) {
                        Toast.makeText(getActivity(), R.string.nothing_to_export, Toast.LENGTH_SHORT).show();
                        return;
                    }

                    String filename = "idecDatabase_full_" + String.valueOf(System.currentTimeMillis()) + ".bundle";
                    ExternalStorage.initStorage();

                    File target = new File(ExternalStorage.rootStorage.getParentFile(), filename);

                    Intent intent = new Intent(getActivity(), DebugActivity.class);
                    intent.putExtra("task", "export_bundle");
                    intent.putExtra("file", target);
                    intent.putExtra("echoareas", echoareas);
                    startActivity(intent);
                }
            });

            Button importConfig = (Button) rootView.findViewById(R.id.additional_database_import_config);
            importConfig.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Activity callingActivity = getActivity();
                    callingActivity.startActivityForResult(new Intent(callingActivity, FileChooserActivity.class), 3);
                }
            });

            Button exportConfig = (Button) rootView.findViewById(R.id.additional_database_export_config);
            exportConfig.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            ExternalStorage.initStorage();
                            String result;
                            try {
                                File toExport = new File(ExternalStorage.rootStorage.getParentFile(), "idecConfig_" + String.valueOf(System.currentTimeMillis()) + ".obj");
                                if (!toExport.exists() && !toExport.createNewFile())
                                    throw new IOException(getString(R.string.create_file_error) + " " + toExport.getAbsolutePath());

                                FileOutputStream os = new FileOutputStream(toExport);
                                ObjectOutputStream oos = new ObjectOutputStream(os);
                                oos.writeObject(Config.values);
                                oos.close();
                                os.close();

                                result = getString(R.string.config_saved) + " " + toExport.getAbsolutePath();
                            } catch (Exception e) {
                                e.printStackTrace();
                                SimpleFunctions.debug(e.toString());
                                result = getString(R.string.error_formatted, e.toString());
                            }

                            final String finalResult = result;
                            getActivity().runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    Toast.makeText(getActivity(), finalResult, Toast.LENGTH_SHORT).show();
                                }
                            });
                        }
                    }).start();
                }
            });

            return rootView;
        }

        public void updateEchoList() {
            echolist.clear();
            echolist.addAll(transport.fullEchoList());
            spinner_adapter.notifyDataSetChanged();
        }
    }

    public static class Blacklist_Fragment extends Fragment {
        public Blacklist_Fragment() {
        }

        public static Blacklist_Fragment newInstance() {
            return new Blacklist_Fragment();
        }

        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
            View rootView = inflater.inflate(R.layout.fragment_additional_blacklist, container, false);
            final Spinner spinner = (Spinner) rootView.findViewById(R.id.additional_blacklist_station_spinner);

            ArrayList<String> stationNames = new ArrayList<>();
            for (Station station : Config.values.stations) {
                stationNames.add(station.nodename);
            }
            ArrayAdapter<String> adapter = new ArrayAdapter<>(getContext(), android.R.layout.simple_spinner_dropdown_item, stationNames);
            spinner.setAdapter(adapter);

            Button selectFile = (Button) rootView.findViewById(R.id.additional_select_file_blacklist);
            selectFile.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Activity callingActivity = getActivity();
                    callingActivity.startActivityForResult(new Intent(callingActivity, FileChooserActivity.class), 1);
                }
            });

            Button clearButton = (Button) rootView.findViewById(R.id.additional_blacklist_clear);
            clearButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Intent debugIntent = new Intent(getActivity(), DebugActivity.class);
                    debugIntent.putExtra("task", "blacklist_clear");
                    startActivity(debugIntent);
                }
            });

            Button downloadBlacklist = (Button) rootView.findViewById(R.id.additional_load_blacklist);
            downloadBlacklist.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Station station = Config.values.stations.get(spinner.getSelectedItemPosition());
                    final String url = station.address + "blacklist.txt";

                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            final Activity mContext = getActivity();
                            String blacklist = Network.getFile(mContext, url, null, Config.values.connectionTimeout);

                            if (blacklist == null) {
                                mContext.runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        Toast.makeText(mContext, R.string.blacklist_download_error, Toast.LENGTH_SHORT).show();
                                    }
                                });
                                return;
                            }

                            ExternalStorage.initStorage();

                            File blacklist_file = new File(ExternalStorage.rootStorage, Blacklist.filename);
                            if (!blacklist_file.exists()) try {
                                boolean created = blacklist_file.createNewFile();
                                if (!created)
                                    throw new IOException("Blacklist file was not created");
                            } catch (IOException e) {
                                SimpleFunctions.debug(e.toString());
                                e.printStackTrace();
                                mContext.runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        Toast.makeText(mContext, R.string.create_file_error, Toast.LENGTH_SHORT).show();
                                    }
                                });
                                return;
                            }

                            try {
                                FileOutputStream fos = new FileOutputStream(blacklist_file);
                                fos.write(blacklist.getBytes());
                                fos.close();

                                mContext.runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        Toast.makeText(mContext, R.string.blacklist_saved, Toast.LENGTH_SHORT).show();
                                    }
                                });
                            } catch (Exception e) {
                                e.printStackTrace();
                                final String error = getString(R.string.blacklist_save_error) + " " + e.toString();
                                SimpleFunctions.debug(error);

                                mContext.runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        Toast.makeText(mContext, error, Toast.LENGTH_SHORT).show();
                                    }
                                });
                                return;
                            }
                            Blacklist.loadBlacklist();
                            mContext.runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    Toast.makeText(mContext, R.string.blacklist_loaded, Toast.LENGTH_SHORT).show();
                                }
                            });
                        }
                    }).start();
                }
            });

            Button deleteBlacklistFile = (Button) rootView.findViewById(R.id.additional_delete_blacklist_file);
            deleteBlacklistFile.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    ExternalStorage.initStorage();
                    File blacklist_file = new File(ExternalStorage.rootStorage, Blacklist.filename);
                    String result;

                    if (blacklist_file.exists()) {
                        boolean deleted = blacklist_file.delete();
                        if (deleted) result = getString(R.string.blacklist_deleted);
                        else result = getString(R.string.blacklist_deletion_error);
                    } else result = getString(R.string.nothing_to_delete);

                    Toast.makeText(getActivity(), result, Toast.LENGTH_SHORT).show();
                }
            });

            return rootView;
        }
    }

    public class SectionsPagerAdapter extends FragmentPagerAdapter {

        public SectionsPagerAdapter(FragmentManager fm) {
            super(fm);
        }

        @Override
        public Fragment getItem(int position) {
            switch (position) {
                case 0:
                    return xfile_Fragment.newInstance();
                case 1:
                    return Database_Fragment.newInstance();
                case 2:
                    return Blacklist_Fragment.newInstance();
                case 3:
                    return FileUploadFragment.newInstance();
                default:
                    return null;
            }
        }

        @Override
        public int getCount() {
            return 4;
        }

        @Override
        public CharSequence getPageTitle(int position) {
            switch (position) {
                case 0:
                    return getString(R.string.node_files);
                case 1:
                    return getString(R.string.database);
                case 2:
                    return getString(R.string.blacklist);
                case 3:
                    return getString(R.string.file_echoareas);
            }
            return null;
        }
    }
}
