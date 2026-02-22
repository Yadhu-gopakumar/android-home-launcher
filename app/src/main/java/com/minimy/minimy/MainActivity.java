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
import android.view.inputmethod.InputMethodManager;
import android.app.WallpaperManager;
import android.view.inputmethod.EditorInfo;
import android.view.KeyEvent;
import java.net.URLEncoder;
import android.app.ActivityOptions;

public class MainActivity extends Activity implements AppAdapter.OnAppActionListener {

    private RecyclerView recyclerView;
    private AppAdapter adapter;
    private List<AppModel> allApps = new ArrayList<>();
    private List<AppModel> homeApps = new ArrayList<>();
    private List<String> hiddenApps = new ArrayList<>();
    private TextView clockView;
    private TextView batteryTextView; 
    private TextView btnAllApps; 
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
        
        // 1. Kill the entrance animation immediately
        overridePendingTransition(0, 0);

        // 2. Set the UI flags for a clean, immersive look
        getWindow().getDecorView().setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
        );
        
        // 3. Set Status Bar to TRANSPARENT so the wallpaper shows through
        getWindow().setStatusBarColor(android.graphics.Color.TRANSPARENT);
        getWindow().setNavigationBarColor(android.graphics.Color.BLACK);

        setContentView(R.layout.activity_main);

        // --- SILENT BACKGROUND WALLPAPER FIX ---
        new Thread(() -> {
            try {
                WallpaperManager wallpaperManager = WallpaperManager.getInstance(getApplicationContext());
                // Create a tiny blank image in RAM and fill it with pure black
                android.graphics.Bitmap blackBitmap = android.graphics.Bitmap.createBitmap(100, 100, android.graphics.Bitmap.Config.ARGB_8888);
                blackBitmap.eraseColor(android.graphics.Color.BLACK);
                // Force Vivo to use this as the system wallpaper
                wallpaperManager.setBitmap(blackBitmap);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
        // ---------------------------------------
        // --- 1. THE INSTANT WEB SEARCH ---
        EditText webSearchBar = findViewById(R.id.et_web_search);
        webSearchBar.setOnEditorActionListener((v, actionId, event) -> {
            // If the user presses the "Search" / "Enter" button on their keyboard
            if (actionId == EditorInfo.IME_ACTION_SEARCH || 
               (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {
                
                String query = webSearchBar.getText().toString().trim();
                if (!query.isEmpty()) {
                    try {
                        // Encode the text so spaces don't break the URL
                        String encodedQuery = URLEncoder.encode(query, "UTF-8");
                        String searchUrl = "https://www.google.com/search?q=" + encodedQuery;
                        
                        // Fire it directly into Chrome's engine
                        Intent searchIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(searchUrl));
                        searchIntent.setPackage("com.android.chrome");
                        searchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        startActivity(searchIntent);
                        
                        // Clear the text box for next time
                        webSearchBar.setText("");
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
                return true; // We handled the event
            }
            return false;
        });

        // --- 2. THE INSTANT INCOGNITO BUTTON ---

        clockView = findViewById(R.id.clock);
        batteryTextView = findViewById(R.id.tv_charging); 
        recyclerView = findViewById(R.id.appList);
        searchBar = findViewById(R.id.searchBar);
        dockView = findViewById(R.id.dock);
        btnAllApps = findViewById(R.id.btn_all_apps); 

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setHasFixedSize(true); 
        recyclerView.setItemViewCacheSize(20); 
        recyclerView.setDrawingCacheEnabled(true); 
        
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
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void saveHiddenApps() {
        try (FileOutputStream fos = openFileOutput(HIDDEN_FILE, Context.MODE_PRIVATE)) {
            for (String pkg : hiddenApps) {
                fos.write((pkg + "\n").getBytes());
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
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
        // Create the zero-animation bundle once for the whole dock
        android.os.Bundle instantOpen = android.app.ActivityOptions.makeCustomAnimation(this, 0, 0).toBundle();

        // --- CAMERA ---
        findViewById(R.id.btn_camera).setOnClickListener(v -> {
            try {
                Intent i = new Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA);
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); 
                startActivity(i, instantOpen); // <-- BUNDLE ADDED HERE
            } catch (Exception e) {
                Intent fallback = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                fallback.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); 
                startActivity(fallback, instantOpen); // <-- BUNDLE ADDED HERE
            }
        });

        // --- CONTACTS ---
        findViewById(R.id.btn_contacts).setOnClickListener(v -> {
            Intent i = new Intent(Intent.ACTION_VIEW, ContactsContract.Contacts.CONTENT_URI);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); 
            startActivity(i, instantOpen); // <-- BUNDLE ADDED HERE
        });

        // --- MUSIC ---
        findViewById(R.id.btn_music).setOnClickListener(v -> {
            Intent i = getPackageManager().getLaunchIntentForPackage("com.mta.intertune");
            if (i != null) {
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); 
                startActivity(i, instantOpen); // <-- BUNDLE ADDED HERE
            } else {
                Toast.makeText(this, "Intertune app not found", Toast.LENGTH_SHORT).show();
            }
        });

        // --- MESSAGES ---
        findViewById(R.id.btn_messages).setOnClickListener(v -> {
            try {
                Intent i = new Intent(Intent.ACTION_MAIN);
                i.addCategory(Intent.CATEGORY_APP_MESSAGING);
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); 
                startActivity(i, instantOpen); // <-- BUNDLE ADDED HERE
            } catch (Exception e) {
                Intent fallback = new Intent(Intent.ACTION_VIEW);
                fallback.setData(android.net.Uri.parse("sms:"));
                fallback.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); 
                startActivity(fallback, instantOpen); // <-- BUNDLE ADDED HERE
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

        // 1. Read the list of package names from your text file
        List<String> savedPackages = new ArrayList<>();
        if (file.exists()) {
            try (BufferedReader br = new BufferedReader(new FileReader(file))) {
                String line;
                while ((line = br.readLine()) != null) {
                    savedPackages.add(line.trim());
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        // 2. Simply match the package names against your allApps list
        // No icons are loaded here. The AppAdapter will handle the SVGs.
        for (AppModel app : allApps) {
            if (savedPackages.contains(app.packageName)) {
                homeApps.add(app);
            }
        }
    }

    private void setupSearch() {
        searchBar.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                String query = s.toString().trim().toLowerCase();

                // Get these ready for the new constructor
                PackageManager pm = getPackageManager();
                int sizePx = (int) (42 * getResources().getDisplayMetrics().density);

                if (query.isEmpty()) {
                    // Updated with pm and sizePx
                    adapter = new AppAdapter(getVisibleApps(), false, MainActivity.this, pm, sizePx);
                } else {
                    List<AppModel> filteredList = new ArrayList<>();
                    for (AppModel app : allApps) {
                        if (app.label.toLowerCase().contains(query)) {
                            filteredList.add(app);
                        }
                    }
                    // Updated with pm and sizePx
                    adapter = new AppAdapter(filteredList, false, MainActivity.this, pm, sizePx);
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
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void updateAdapter(List<AppModel> list, boolean isHome) {
        int sizePx = (int) (42 * getResources().getDisplayMetrics().density);
        // Passing the 5 arguments: list, isHome, listener, packageManager, iconSize
        adapter = new AppAdapter(list, isHome, this, getPackageManager(), sizePx);
        recyclerView.setAdapter(adapter);
    }

   
    private void showAllApps() {
        isHomeState = false;
        
        // 1. Hide the entire Home Screen group (Clock + Google Search)
        findViewById(R.id.header_container).setVisibility(View.GONE);
        if (dockView != null) dockView.setVisibility(View.GONE);
        if (btnAllApps != null) btnAllApps.setVisibility(View.GONE);
        
        // 2. Show the Local App Search
        if (searchBar != null) searchBar.setVisibility(View.VISIBLE);

        // 3. Move the app list under the local search bar
        try {
            RelativeLayout.LayoutParams params = (RelativeLayout.LayoutParams) recyclerView.getLayoutParams();
            params.removeRule(RelativeLayout.BELOW);
            params.addRule(RelativeLayout.BELOW, R.id.searchBar);
            recyclerView.setLayoutParams(params);
        } catch (Exception e) { e.printStackTrace(); }

        // 4. Trigger the list update
        if (searchBar != null) searchBar.setText(""); 
    }

    private void showHome() {
        isHomeState = true;
        
        // 1. Show the entire Home Screen group (Clock + Google Search)
        findViewById(R.id.header_container).setVisibility(View.VISIBLE);
        if (dockView != null) dockView.setVisibility(View.VISIBLE);
        if (btnAllApps != null) btnAllApps.setVisibility(View.VISIBLE);
        
        // 2. Hide the Local App Search
        if (searchBar != null) searchBar.setVisibility(View.GONE);
        
        // 3. Move the app list back under the header_container
        try {
            RelativeLayout.LayoutParams params = (RelativeLayout.LayoutParams) recyclerView.getLayoutParams();
            params.removeRule(RelativeLayout.BELOW);
            params.addRule(RelativeLayout.BELOW, R.id.header_container);
            recyclerView.setLayoutParams(params);
        } catch (Exception e) { e.printStackTrace(); }

        // 4. Show only the home apps
        updateAdapter(homeApps, true);
    }

    private void startClock() {
        handler.post(new Runnable() {
            @Override
            public void run() {
                clockView.setText(new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date()));

                if (isHomeState) {
                    IntentFilter ifilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
                    Intent batteryStatus = registerReceiver(null, ifilter);

                    if (batteryStatus != null) {
                        int level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
                        int scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
                        int percentage = (int) ((level / (float) scale) * 100);

                        if (batteryTextView != null) {
                            batteryTextView.setText(percentage + "%");
                        }
                    }
                }
                handler.postDelayed(this, 10000); // 10 seconds is perfect for your 2GB RAM
            }
        });
    }

    @Override
    public void onLaunch(AppModel app) {
        // 1. Launch the app IMMEDIATELY (Highest Priority)
        Intent i = getPackageManager().getLaunchIntentForPackage(app.packageName);
        if (i != null) {
            startActivity(i);
        }

        // 2. Perform UI cleanup after a short delay (Lower Priority)
        if (!isHomeState) {
            new Handler().postDelayed(() -> {
                // Clear search text
                searchBar.setText("");

                // Hide keyboard
                InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                if (imm != null) {
                    imm.hideSoftInputFromWindow(searchBar.getWindowToken(), 0);
                }
            }, 300); // 300ms delay ensures the app is already opening
        }
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
                            for (AppModel homeApp : homeApps) {
                                if (homeApp.packageName.equals(app.packageName)) {
                                    exists = true;
                                }
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
            // If we are in the "All Apps" search, go back to our custom Home
            if (searchBar.getText().length() > 0) {
                searchBar.setText("");
            } else {
                InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                if (imm != null && searchBar.getWindowToken() != null) {
                    imm.hideSoftInputFromWindow(searchBar.getWindowToken(), 0);
                }
                showHome();
            }
        }
    }
}
