package com.minimy.minimy;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.provider.ContactsContract;
import android.provider.MediaStore;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Gravity;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.RelativeLayout;
import android.widget.LinearLayout;
import android.widget.EditText;
import java.util.Map;
import java.util.HashMap;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.text.Editable;
import android.text.TextWatcher;
import android.view.inputmethod.InputMethodManager;
import android.view.inputmethod.EditorInfo;
import android.view.KeyEvent;

import android.app.WallpaperManager;

import java.io.*;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.net.URLEncoder;

public class MainActivity extends Activity implements AppAdapter.OnAppActionListener {

    private RecyclerView recyclerView;
    private AppAdapter adapter;
    private List<AppModel> allApps = new ArrayList<>();
    private List<AppModel> homeApps = new ArrayList<>();
    private List<String> hiddenApps = new ArrayList<>();
    private TextView clockView;
    private TextView btnAllApps; 
    private Handler handler = new Handler();
    private EditText searchBar;
    private View dockView;
    private boolean isHomeState = true;
    private static final String FAV_FILE = "home_apps.txt";
    private static final String HIDDEN_FILE = "hidden_apps.txt";

    private android.content.BroadcastReceiver packageReceiver;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        overridePendingTransition(0, 0);

        getWindow().getDecorView().setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
        );
        
        getWindow().setStatusBarColor(android.graphics.Color.TRANSPARENT);
        getWindow().setNavigationBarColor(android.graphics.Color.BLACK);

        setContentView(R.layout.activity_main);

        // ... existing wallpaper and web search code ...

        clockView = findViewById(R.id.clock);
        recyclerView = findViewById(R.id.appList);
        searchBar = findViewById(R.id.searchBar);
        dockView = findViewById(R.id.dock);
        btnAllApps = findViewById(R.id.btn_all_apps); 

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setHasFixedSize(true); 
        
        btnAllApps.setOnClickListener(v -> showAllApps());

        // INITIALIZE ALL UTILS
        setupDock();
        setupDragToLock(); // CALL THE SLIDER
        loadAllApps();
        loadHiddenApps();
        loadHomeApps();
        startClock();
        setupPackageReceiver();
        setupSearch();
        showHome();
    }

   private void setupDragToLock() {
    final View track = findViewById(R.id.lock_drag_track);
    final View handle = findViewById(R.id.lock_drag_handle);

    if (handle == null || track == null) return;

    handle.setOnTouchListener(new View.OnTouchListener() {
        float dY;
        @Override
        public boolean onTouch(View v, MotionEvent event) {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    // Tactile feedback
                    v.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS);
                    dY = v.getY() - event.getRawY();
                    break;

                case MotionEvent.ACTION_MOVE:
                    float newY = event.getRawY() + dY;
                    
                    // Boundary check: Stay inside the 220dp track
                    if (newY >= 0 && newY <= (track.getHeight() - v.getHeight())) {
                        v.setY(newY);
                        
                        // Optional: Fade the track as you pull down
                        track.setAlpha(0.2f + (newY / track.getHeight()));
                    }
                    break;

                case MotionEvent.ACTION_UP:
                    // If pulled to the very bottom (90% threshold)
                    if (v.getY() > (track.getHeight() - v.getHeight()) * 0.9) {
                        lockScreen();
                        v.setY(0); 
                    } else {
                        // Snappy Spring Back
                        v.animate()
                         .y(0)
                         .setDuration(300)
                         .setInterpolator(new android.view.animation.DecelerateInterpolator())
                         .start();
                    }
                    track.setAlpha(0.2f); // Reset track alpha
                    break;
            }
            return true;
        }
    });
}

    private void setupDock() {
        android.os.Bundle instantOpen = android.app.ActivityOptions.makeCustomAnimation(this, 0, 0).toBundle();

        findViewById(R.id.btn_camera).setOnClickListener(v -> {
            try {
                Intent i = new Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA);
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); 
                startActivity(i, instantOpen);
            } catch (Exception e) {
                Intent fallback = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                startActivity(fallback, instantOpen);
            }
        });

        findViewById(R.id.btn_contacts).setOnClickListener(v -> {
            Intent i = new Intent(Intent.ACTION_VIEW, ContactsContract.Contacts.CONTENT_URI);
            startActivity(i, instantOpen);
        });

        findViewById(R.id.btn_music).setOnClickListener(v -> {
            Intent i = getPackageManager().getLaunchIntentForPackage("com.mta.intertune");
            if (i != null) startActivity(i, instantOpen);
            else Toast.makeText(this, "Music app not found", Toast.LENGTH_SHORT).show();
        });

        findViewById(R.id.btn_messages).setOnClickListener(v -> {
            Intent i = new Intent(Intent.ACTION_MAIN);
            i.addCategory(Intent.CATEGORY_APP_MESSAGING);
            try { startActivity(i, instantOpen); } 
            catch (Exception e) { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("sms:")), instantOpen); }
        });
    }

  private void setupLetterIndex() {
    LinearLayout container = findViewById(R.id.letter_index_container);
    if (container == null) return;
    container.removeAllViews();
    
    final List<AppModel> currentList = getVisibleApps();
    List<String> letters = new ArrayList<>();
    
    for (AppModel app : currentList) {
        if (app.label != null && !app.label.isEmpty()) {
            String firstChar = app.label.substring(0, 1).toUpperCase();
            if (!letters.contains(firstChar)) {
                letters.add(firstChar);
            }
        }
    }
    Collections.sort(letters);

    for (final String letter : letters) {
        TextView tv = new TextView(this);
        tv.setText(letter);
        tv.setTextColor(android.graphics.Color.WHITE);
        tv.setTextSize(16); 
        tv.setTypeface(null, android.graphics.Typeface.BOLD);
        tv.setGravity(Gravity.CENTER);

        // This uses the Weight (1.0f) to squeeze letters together so they all fit
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 
                0, 
                1.0f
        );
        tv.setLayoutParams(params);

        tv.setOnClickListener(v -> {
            v.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY);
            for (int i = 0; i < currentList.size(); i++) {
                if (currentList.get(i).label.toUpperCase().startsWith(letter)) {
                    ((LinearLayoutManager) recyclerView.getLayoutManager())
                        .scrollToPositionWithOffset(i, 0);
                    break;
                }
            }
        });
        container.addView(tv);
    }
}

    private List<AppModel> getVisibleApps() {
        List<AppModel> visible = new ArrayList<>();
        for (AppModel app : allApps) {
            if (!hiddenApps.contains(app.packageName)) visible.add(app);
        }
        return visible;
    }

    private void saveHomeApps() {
        try (FileOutputStream fos = openFileOutput(FAV_FILE, Context.MODE_PRIVATE)) {
            for (AppModel app : homeApps) {
                fos.write((app.packageName + "\n").getBytes());
            }
        } catch (IOException e) { e.printStackTrace(); }
    }

    private void saveHiddenApps() {
        try (FileOutputStream fos = openFileOutput(HIDDEN_FILE, Context.MODE_PRIVATE)) {
            for (String pkg : hiddenApps) {
                fos.write((pkg + "\n").getBytes());
            }
        } catch (IOException e) { e.printStackTrace(); }
    }

    private void loadHiddenApps() {
        hiddenApps.clear();
        File file = new File(getFilesDir(), HIDDEN_FILE);
        if (file.exists()) {
            try (BufferedReader br = new BufferedReader(new FileReader(file))) {
                String line;
                while ((line = br.readLine()) != null) hiddenApps.add(line.trim());
            } catch (IOException e) { e.printStackTrace(); }
        }
    }

    // Create a cache for icons at the top of your class
public static Map<String, android.graphics.drawable.Drawable> iconCache = new HashMap<>();

private void loadAllApps() {
    new Thread(() -> {
        List<AppModel> tempList = new ArrayList<>(); // Use a temporary list
        PackageManager pm = getPackageManager();
        Intent i = new Intent(Intent.ACTION_MAIN, null);
        i.addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> activities = pm.queryIntentActivities(i, 0);
        
        for (ResolveInfo ri : activities) {
            AppModel app = new AppModel();
            app.label = ri.loadLabel(pm).toString();
            app.packageName = ri.activityInfo.packageName;
            
            // Background loading icons into RAM
            iconCache.put(app.packageName, ri.loadIcon(pm));
            tempList.add(app);
        }
        
        Collections.sort(tempList, (a, b) -> a.label.compareToIgnoreCase(b.label));

        // CRITICAL: Switch back to Main Thread to update UI
        runOnUiThread(() -> {
            allApps.clear();
            allApps.addAll(tempList);
            if (!isHomeState) {
                updateAdapter(getVisibleApps(), false);
            }
        });
    }).start();
}
    private void loadHomeApps() {
        homeApps.clear();
        File file = new File(getFilesDir(), FAV_FILE);
        List<String> savedPackages = new ArrayList<>();
        if (file.exists()) {
            try (BufferedReader br = new BufferedReader(new FileReader(file))) {
                String line;
                while ((line = br.readLine()) != null) savedPackages.add(line.trim());
            } catch (IOException e) { e.printStackTrace(); }
        }
        for (AppModel app : allApps) {
            if (savedPackages.contains(app.packageName)) homeApps.add(app);
        }
    }

    private void setupSearch() {
        searchBar.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(Editable s) {
                String query = s.toString().trim().toLowerCase();
                if (query.isEmpty()) updateAdapter(getVisibleApps(), false);
                else {
                    List<AppModel> filteredList = new ArrayList<>();
                    for (AppModel app : allApps) {
                        if (app.label.toLowerCase().contains(query)) filteredList.add(app);
                    }
                    updateAdapter(filteredList, false);
                }
            }
        });
    }

    private void updateAdapter(List<AppModel> list, boolean isHome) {
        int sizePx = (int) (42 * getResources().getDisplayMetrics().density);
        adapter = new AppAdapter(list, isHome, this, getPackageManager(), sizePx);
        recyclerView.setAdapter(adapter);
    }

private void showAllApps() {
    isHomeState = false;
    
    // 1. Hide Header, Show Search
    findViewById(R.id.header_container).setVisibility(View.GONE);
 if (searchBar != null) {
        searchBar.setVisibility(View.VISIBLE);
        // Prevent auto-blinking cursor
        searchBar.clearFocus(); 
        
        // Ensure keyboard doesn't pop up until a click
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(searchBar.getWindowToken(), 0);
    }
    // 2. Hide Navigation/Home elements
    if (dockView != null) dockView.setVisibility(View.GONE);
    if (btnAllApps != null) btnAllApps.setVisibility(View.GONE);

    // 3. Swap Slider for Letters
    if (findViewById(R.id.slider_wrapper) != null) findViewById(R.id.slider_wrapper).setVisibility(View.GONE);
    findViewById(R.id.letter_index_container).setVisibility(View.VISIBLE);

    // 4. FIX POSITIONING: Ensure the list is below the search bar
    View container = findViewById(R.id.all_apps_container);
    RelativeLayout.LayoutParams lp = (RelativeLayout.LayoutParams) container.getLayoutParams();
    lp.addRule(RelativeLayout.BELOW, R.id.searchBar);
    container.setLayoutParams(lp);

    updateAdapter(getVisibleApps(), false); 
    setupLetterIndex();
}

private void showHome() {
    isHomeState = true;
    
    // 1. Show Header, Hide Search
    findViewById(R.id.header_container).setVisibility(View.VISIBLE);
    if (searchBar != null) {
        searchBar.setVisibility(View.GONE);
        searchBar.setText("");
    }
    
    // 2. Show Home elements
    if (dockView != null) dockView.setVisibility(View.VISIBLE);
    if (btnAllApps != null) btnAllApps.setVisibility(View.VISIBLE);

    // 3. Swap Letters for Slider
    if (findViewById(R.id.slider_wrapper) != null) findViewById(R.id.slider_wrapper).setVisibility(View.VISIBLE);
    findViewById(R.id.letter_index_container).setVisibility(View.GONE);

    // 4. FIX POSITIONING: Move list back below the Clock/Header
    View container = findViewById(R.id.all_apps_container);
    RelativeLayout.LayoutParams lp = (RelativeLayout.LayoutParams) container.getLayoutParams();
    lp.addRule(RelativeLayout.BELOW, R.id.header_container); // Anchor to clock
    container.setLayoutParams(lp);

    updateAdapter(homeApps, true);
}
    private void startClock() {
        handler.post(new Runnable() {
            @Override
            public void run() {
                clockView.setText(new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date()));
                
                handler.postDelayed(this, 10000); 
            }
        });
    }

    private void setupPackageReceiver() {
        packageReceiver = new android.content.BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (Intent.ACTION_PACKAGE_REMOVED.equals(intent.getAction())) {
                    String pkg = intent.getData().getSchemeSpecificPart();
                    File file = new File(getCacheDir(), "icon_" + pkg);
                    if (file.exists()) file.delete();
                }
                loadAllApps();
                loadHomeApps();
                if (isHomeState) showHome();
                else showAllApps();
            }
        };
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_PACKAGE_ADDED);
        filter.addAction(Intent.ACTION_PACKAGE_REMOVED);
        filter.addAction(Intent.ACTION_PACKAGE_REPLACED);
        filter.addDataScheme("package");
        registerReceiver(packageReceiver, filter);
    }

  @Override
public void onLaunch(AppModel app) {
    Intent i = getPackageManager().getLaunchIntentForPackage(app.packageName);
    if (i != null) {
        // 1. Force the activity to be treated as a separate task with NO transition
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK 
                 | Intent.FLAG_ACTIVITY_NO_ANIMATION);

        // 2. The Zero-Animation Bundle
        android.os.Bundle instantOpen = android.app.ActivityOptions.makeCustomAnimation(this, 0, 0).toBundle();
        
        startActivity(i, instantOpen);
        
        // 3. Global override - this is the "Nuclear Option" for animations
        overridePendingTransition(0, 0);
    }

    if (!isHomeState) {
        // Delay the keyboard hide slightly so it doesn't fight the app launch for CPU
        new android.os.Handler().postDelayed(() -> {
            if (searchBar != null) searchBar.setText("");
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null && searchBar != null) {
                imm.hideSoftInputFromWindow(searchBar.getWindowToken(), 0);
            }
        }, 100); 
    }
}
// Inside your MainActivity.java
private void lockScreen() {
    DevicePolicyManager dpm = (DevicePolicyManager) getSystemService(Context.DEVICE_POLICY_SERVICE);
    ComponentName adminComponent = new ComponentName(this, MyAdminReceiver.class);

    if (dpm.isAdminActive(adminComponent)) {
        dpm.lockNow();
    } else {
        // Ask the user to enable "Device Admin" so the app has permission to turn off the screen
        Intent intent = new Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN);
        intent.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent);
        intent.putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "Needed to lock the screen via gesture.");
        startActivity(intent);
    }
}
 @Override
public void onLongPress(AppModel app) {
    // 1. Find the specific view that was pressed to animate it
    // Note: This works best if you pass the View from the Adapter to the listener
    // But for now, let's just focus on the Logic:
    
    // 2. Vibration feedback (Tactile feel)
    getWindow().getDecorView().performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS);

    if (isHomeState) {
        new AlertDialog.Builder(this, AlertDialog.THEME_DEVICE_DEFAULT_DARK)
            .setTitle("Remove " + app.label + "?")
            .setPositiveButton("Yes", (d, w) -> {
                homeApps.remove(app);
                saveHomeApps();
                updateAdapter(homeApps, true);
            }).setNegativeButton("No", null).show();
    } else {
        boolean isHidden = hiddenApps.contains(app.packageName);
        String[] options = {"Add to Home", isHidden ? "Unhide" : "Hide", "Uninstall"};
        
        new AlertDialog.Builder(this, AlertDialog.THEME_DEVICE_DEFAULT_DARK)
            .setTitle(app.label.toUpperCase()) // Make title stand out
            .setItems(options, (dialog, which) -> {
                if (which == 0) {
                    boolean exists = false;
                    for(AppModel h : homeApps) if(h.packageName.equals(app.packageName)) exists = true;
                    if (!exists) { 
                        homeApps.add(app); 
                        saveHomeApps(); 
                        Toast.makeText(this, "Added to Home", Toast.LENGTH_SHORT).show();
                    }
                } else if (which == 1) {
                    if (isHidden) hiddenApps.remove(app.packageName);
                    else hiddenApps.add(app.packageName);
                    saveHiddenApps();
                    updateAdapter(getVisibleApps(), false);
                } else if (which == 2) {
                    startActivity(new Intent(Intent.ACTION_DELETE, Uri.parse("package:" + app.packageName)));
                }
            }).show();
    }
}

    @Override
    public void onResume() {
        super.onResume();
        if (allApps.isEmpty()) loadAllApps();
        if (isHomeState) showHome();
    }

    @Override
    public void onBackPressed() {
        if (!isHomeState) {
            if (searchBar.getText().length() > 0) searchBar.setText("");
            else showHome();
        }
    }
}