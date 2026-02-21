package com.minimy.minimy;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri; 
import android.os.Bundle;
import android.os.Handler;
import android.provider.ContactsContract;
import android.provider.MediaStore;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.RelativeLayout;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.EditText;

public class MainActivity extends Activity implements AppAdapter.OnAppActionListener {

    private RecyclerView recyclerView;
    private AppAdapter adapter;
    private List<AppModel> allApps = new ArrayList<>();
    private List<AppModel> homeApps = new ArrayList<>();
    private List<String> hiddenApps = new ArrayList<>();
    private TextView clockView;
    private TextView btnAllApps; // The new floating button
    private Handler handler = new Handler();
    private EditText searchBar;
    private View dockView;
    private boolean isHomeState = true; 
    private static final String FAV_FILE = "home_apps.txt";
    private static final String HIDDEN_FILE = "hidden_apps.txt";
    
    private android.content.BroadcastReceiver packageReceiver;
    private TextView chargingView; // NEW
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        clockView = findViewById(R.id.clock);
        chargingView = findViewById(R.id.tv_charging); // NEW
        recyclerView = findViewById(R.id.appList);
        searchBar = findViewById(R.id.searchBar);
        dockView = findViewById(R.id.dock);
        btnAllApps = findViewById(R.id.btn_all_apps); // Link the UI button
        
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        // Set click listener for floating All Apps button
        btnAllApps.setOnClickListener(v -> showAllApps());

        setupDock();
        loadAllApps();
        loadHiddenApps();
        loadHomeApps();
        startClock();
        setupPackageReceiver(); 
        setupSearch(); 
        showHome();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadAllApps();
        loadHiddenApps();
        loadHomeApps();
        if (isHomeState) {
            showHome();
        } else {
            if (searchBar.getVisibility() == View.VISIBLE && !searchBar.getText().toString().isEmpty()) {
                searchBar.setText(searchBar.getText().toString()); 
            } else {
                showAllApps();
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Turn off the antenna if the app is killed
        if (packageReceiver != null) {
            unregisterReceiver(packageReceiver);
        }
    }
    private void setupPackageReceiver() {
        packageReceiver = new android.content.BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                // A system change happened! Refresh our internal lists.
                loadAllApps();
                loadHomeApps(); 
                
                // Update the UI smoothly without the user doing anything
                if (isHomeState) {
                    showHome();
                } else {
                    // If they are currently typing in the search bar, re-filter it
                    if (searchBar.getVisibility() == View.VISIBLE && !searchBar.getText().toString().isEmpty()) {
                        searchBar.setText(searchBar.getText().toString()); 
                    } else {
                        showAllApps();
                    }
                }
            }
        };

        // Create the "Radio Filter" to only listen for app changes
        android.content.IntentFilter filter = new android.content.IntentFilter();
        filter.addAction(Intent.ACTION_PACKAGE_ADDED);
        filter.addAction(Intent.ACTION_PACKAGE_REMOVED);
        filter.addAction(Intent.ACTION_PACKAGE_REPLACED);
        
        // CRITICAL: Android requires this specific scheme to hear app installs!
        filter.addDataScheme("package"); 

        registerReceiver(packageReceiver, filter);
    }

    private void loadHiddenApps() {
        hiddenApps.clear();
        File file = new File(getFilesDir(), HIDDEN_FILE);
        if (file.exists()) {
            try (BufferedReader br = new BufferedReader(new FileReader(file))) {
                String line;
                while ((line = br.readLine()) != null) {
                    hiddenApps.add(line.trim());
                }
            } catch (IOException e) { e.printStackTrace(); }
        }
    }

    private void saveHiddenApps() {
        try (FileOutputStream fos = openFileOutput(HIDDEN_FILE, Context.MODE_PRIVATE)) {
            for (String pkg : hiddenApps) {
                fos.write((pkg + "\n").getBytes());
            }
        } catch (IOException e) { e.printStackTrace(); }
    }

    private List<AppModel> getVisibleApps() {
        List<AppModel> visible = new ArrayList<>();
        for (AppModel app : allApps) {
            if (!hiddenApps.contains(app.packageName)) {
                visible.add(app);
            }
        }
        return visible;
    }

    private void setupDock() {
        findViewById(R.id.btn_camera).setOnClickListener(v -> {
            try { startActivity(new Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)); } 
            catch (Exception e) { startActivity(new Intent(MediaStore.ACTION_IMAGE_CAPTURE)); }
        });

        findViewById(R.id.btn_contacts).setOnClickListener(v -> {
            startActivity(new Intent(Intent.ACTION_VIEW, ContactsContract.Contacts.CONTENT_URI));
        });

        findViewById(R.id.btn_music).setOnClickListener(v -> {
            Intent i = getPackageManager().getLaunchIntentForPackage("com.mta.intertune");
            if (i != null) startActivity(i);
            else Toast.makeText(this, "Intertune app not found", Toast.LENGTH_SHORT).show();
        });

        findViewById(R.id.btn_messages).setOnClickListener(v -> {
            try {
                Intent i = new Intent(Intent.ACTION_MAIN);
                i.addCategory(Intent.CATEGORY_APP_MESSAGING);
                startActivity(i);
            } catch (Exception e) {
                Intent fallback = new Intent(Intent.ACTION_VIEW);
                fallback.setData(Uri.parse("sms:"));
                startActivity(fallback);
            }
        });
    }

    private void loadAllApps() {
        allApps.clear();
        PackageManager pm = getPackageManager();
        Intent i = new Intent(Intent.ACTION_MAIN, null);
        i.addCategory(Intent.CATEGORY_LAUNCHER);

        List<ResolveInfo> activities = pm.queryIntentActivities(i, 0);
        for (ResolveInfo ri : activities) {
            AppModel app = new AppModel();
            app.label = ri.loadLabel(pm).toString();
            app.packageName = ri.activityInfo.packageName;
            allApps.add(app);
        }
        Collections.sort(allApps, (a, b) -> a.label.compareToIgnoreCase(b.label));
    }

    private void loadHomeApps() {
        homeApps.clear();
        File file = new File(getFilesDir(), FAV_FILE);
        
        List<String> savedPackages = new ArrayList<>();
        if (file.exists()) {
            try (BufferedReader br = new BufferedReader(new FileReader(file))) {
                String line;
                while ((line = br.readLine()) != null) {
                    savedPackages.add(line.trim());
                }
            } catch (IOException e) { e.printStackTrace(); }
        }

        for (AppModel app : allApps) {
            if (savedPackages.contains(app.packageName)) {
                homeApps.add(app);
            }
        }
        // Notice we REMOVED the dummy "All Apps" button from the list here!
    }

    private void setupSearch() {
        searchBar.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                String query = s.toString().toLowerCase();
                
                if (query.isEmpty()) {
                    adapter = new AppAdapter(getVisibleApps(), false, MainActivity.this);
                } else {
                    List<AppModel> filteredList = new ArrayList<>();
                    for (AppModel app : allApps) {
                        if (app.label.toLowerCase().contains(query)) {
                            filteredList.add(app);
                        }
                    }
                    adapter = new AppAdapter(filteredList, false, MainActivity.this);
                }
                recyclerView.setAdapter(adapter);
            }
        });
    }

    private void saveHomeApps() {
        try (FileOutputStream fos = openFileOutput(FAV_FILE, Context.MODE_PRIVATE)) {
            for (AppModel app : homeApps) {
                fos.write((app.packageName + "\n").getBytes());
            }
        } catch (IOException e) { e.printStackTrace(); }
    }

    private void showHome() {
        isHomeState = true;
        clockView.setVisibility(View.VISIBLE);
        dockView.setVisibility(View.VISIBLE);
        searchBar.setVisibility(View.GONE);
        btnAllApps.setVisibility(View.VISIBLE); // Show floating button
        
        RelativeLayout.LayoutParams params = (RelativeLayout.LayoutParams) recyclerView.getLayoutParams();
        params.removeRule(RelativeLayout.BELOW); 
        params.addRule(RelativeLayout.BELOW, R.id.header_container); // UPDATED ID
        recyclerView.setLayoutParams(params);

        adapter = new AppAdapter(homeApps, true, this); 
        recyclerView.setAdapter(adapter);
    }

    private void showAllApps() {
        isHomeState = false;
        clockView.setVisibility(View.GONE);
        dockView.setVisibility(View.GONE);
        btnAllApps.setVisibility(View.GONE); // Hide floating button
        searchBar.setVisibility(View.VISIBLE);
        searchBar.setText(""); 
        
        // Also hide the charging text when searching!
        chargingView.setVisibility(View.GONE);

        RelativeLayout.LayoutParams params = (RelativeLayout.LayoutParams) recyclerView.getLayoutParams();
        params.removeRule(RelativeLayout.BELOW); 
        params.addRule(RelativeLayout.BELOW, R.id.searchBar); 
        recyclerView.setLayoutParams(params);

        adapter = new AppAdapter(getVisibleApps(), false, this);
        recyclerView.setAdapter(adapter);
    }

private void startClock() {
        handler.post(new Runnable() {
            @Override
            public void run() {
                // 1. Update Time
                clockView.setText(new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date()));
                
                // 2. Update Charging Status (Only check if we are looking at the Home screen)
                if (isHomeState) {
                    IntentFilter ifilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
                    Intent batteryStatus = registerReceiver(null, ifilter);
                    
                    if (batteryStatus != null) {
                        int status = batteryStatus.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
                        boolean isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                                             status == BatteryManager.BATTERY_STATUS_FULL;
                        
                        if (isCharging) {
                            // Calculate percentage
                            int level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
                            int scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
                            int batteryPct = (int) ((level / (float) scale) * 100);
                            
                            chargingView.setText("⚡ Charging " + batteryPct + "%");
                            chargingView.setVisibility(View.VISIBLE);
                        } else {
                            chargingView.setVisibility(View.GONE);
                        }
                    }
                }

                // 3. Loop every 10 seconds
                handler.postDelayed(this, 10000);
            }
        });
    }

    @Override
    public void onLaunch(AppModel app) {
        Intent i = getPackageManager().getLaunchIntentForPackage(app.packageName);
        if (i != null) startActivity(i);
    }

    @Override
    public void onLongPress(AppModel app) {
        if (isHomeState) {
            new AlertDialog.Builder(this, AlertDialog.THEME_DEVICE_DEFAULT_DARK)
                .setTitle("Remove?")
                .setMessage("Remove " + app.label + " from Home?")
                .setPositiveButton("Yes", (d, w) -> {
                    homeApps.remove(app);
                    saveHomeApps();
                    adapter.notifyDataSetChanged(); 
                })
                .setNegativeButton("No", null)
                .show();
        } else {
            boolean isHidden = hiddenApps.contains(app.packageName);
            String hideOptionText = isHidden ? "Unhide App" : "Hide App";
            
            String[] options = {"Add to Home", hideOptionText, "Uninstall"};

            new AlertDialog.Builder(this, AlertDialog.THEME_DEVICE_DEFAULT_DARK)
                .setTitle(app.label)
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        boolean exists = false;
                        for(AppModel homeApp : homeApps) {
                            if(homeApp.packageName.equals(app.packageName)) exists = true;
                        }
                        if (!exists) {
                            homeApps.add(app); 
                            saveHomeApps();
                            Toast.makeText(this, "Added to Home", Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(this, "Already on Home screen", Toast.LENGTH_SHORT).show();
                        }

                    } else if (which == 1) {
                        if (isHidden) {
                            hiddenApps.remove(app.packageName);
                            Toast.makeText(this, app.label + " is now visible", Toast.LENGTH_SHORT).show();
                        } else {
                            hiddenApps.add(app.packageName);
                            Toast.makeText(this, app.label + " is hidden", Toast.LENGTH_SHORT).show();
                        }
                        saveHiddenApps();
                        searchBar.setText(searchBar.getText().toString()); 

                    } else if (which == 2) {
                        Intent intent = new Intent(Intent.ACTION_DELETE);
                        intent.setData(Uri.parse("package:" + app.packageName));
                        startActivity(intent);
                    }
                })
                .show();
        }
    }

    @Override
    public void onBackPressed() {
        if (!isHomeState) {
            showHome(); 
        }
    }
}
