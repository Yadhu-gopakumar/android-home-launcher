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
import android.graphics.drawable.Drawable; // <-- ADDED THIS IMPORT
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
import android.view.GestureDetector;
import androidx.browser.customtabs.CustomTabsClient;
import androidx.browser.customtabs.CustomTabsSession;
import androidx.browser.customtabs.CustomTabsServiceConnection;
import androidx.browser.customtabs.CustomTabsIntent;

import java.io. * ;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity implements AppAdapter.OnAppActionListener {
  private CustomTabsServiceConnection mServiceConnection;
  private Handler handler = new Handler();
  private Runnable clockRunnable = new Runnable() {@Override
    public void run() {
      if (clockView != null) {
        clockView.setText(new SimpleDateFormat("hh:mm", Locale.getDefault()).format(new Date()));
      }
      // Update every 60 seconds
      handler.postDelayed(this, 60000);
    }
  };
  private CustomTabsClient mClient;
  private CustomTabsSession mSession;
  private RecyclerView recyclerView;
  private AppAdapter adapter;
  private List < AppModel > allApps = new ArrayList < >();
  private List < AppModel > homeApps = new ArrayList < >();
  private List < String > hiddenApps = new ArrayList < >();
  private TextView clockView;
  private TextView btnAllApps;
  private EditText searchBar;
  private View dockView;
  private boolean isHomeState = true;
  private static final String FAV_FILE = "home_apps.txt";
  private static final String HIDDEN_FILE = "hidden_apps.txt";
  private EditText webSearchBar;
  private android.content.BroadcastReceiver packageReceiver;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    overridePendingTransition(0, 0);

    // Make it truly fullscreen
    getWindow().getDecorView().setSystemUiVisibility(
    View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);

    getWindow().setStatusBarColor(android.graphics.Color.TRANSPARENT);
    getWindow().setNavigationBarColor(android.graphics.Color.BLACK);

    setContentView(R.layout.activity_main);

    // UI Components
    clockView = findViewById(R.id.clock);
    recyclerView = findViewById(R.id.appList);
    searchBar = findViewById(R.id.searchBar);
    webSearchBar = findViewById(R.id.et_web_search);
    dockView = findViewById(R.id.dock);

    // Setup RecyclerView
    recyclerView.setLayoutManager(new LinearLayoutManager(this));
    recyclerView.setHasFixedSize(true);

    // INITIALIZE ALL UTILS
    setupDock();
    loadDockIcons();
    setupDragToLock();
    loadAllApps();
    loadHiddenApps();
    setupPackageReceiver();
    setupSearch();
    setupWebSearch();
    setupSwipeGesture();
    showHome();
  }

  private void setupSwipeGesture() {
    View root = findViewById(R.id.root_layout);
    if (root == null) return;

    android.view.GestureDetector gestureDetector = new android.view.GestureDetector(this, new android.view.GestureDetector.SimpleOnGestureListener() {@Override
      public boolean onFling(android.view.MotionEvent e1, android.view.MotionEvent e2, float velocityX, float velocityY) {
        if (e1 == null || e2 == null) return false;

        float diffY = e2.getY() - e1.getY();

        if (Math.abs(diffY) > 100 && Math.abs(velocityY) > 100) {
          if (diffY < 0) {
            // Swiped UP
            if (isHomeState) {
              showAllApps();
              return true; // Return TRUE because we handled the gesture
            }
          } else {
            // Swiped DOWN
            if (!isHomeState) {
              // Only close if the list is at the absolute top
              if (recyclerView != null && !recyclerView.canScrollVertically( - 1)) {
                showHome();
                return true; // Return TRUE because we handled the gesture
              }
              // If it CAN scroll up, return false so the RecyclerView scrolls normally
              return false;
            }
          }
        }
        return false;
      }
    });

    View.OnTouchListener touchListener = (v, event) ->{
      // 1. Let the detector analyze the touch first
      boolean handled = gestureDetector.onTouchEvent(event);

      // 2. CRITICAL FIX: Kill the momentum if the drawer state changed
      if (handled) {
        event.setAction(android.view.MotionEvent.ACTION_CANCEL);
      }

      // 3. Always return false so standard taps and slow scrolls still work perfectly
      return false;
    };

    root.setOnTouchListener(touchListener);
    if (recyclerView != null) {
      recyclerView.setOnTouchListener(touchListener);
    }
  }
  // --- NEW METHOD FOR ASYNC DOCK ICONS ---
  private void loadDockIcons() {
    new Thread(() ->{
      PackageManager pm = getPackageManager();

      Drawable camIcon = null;
      Drawable msgIcon = null;
      Drawable contactsIcon = null;
      Drawable musicIcon = null;
      boolean hasMusic = false;

      try {
        Intent camIntent = new Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA);
        ResolveInfo camInfo = pm.resolveActivity(camIntent, PackageManager.MATCH_DEFAULT_ONLY);
        if (camInfo != null) camIcon = camInfo.loadIcon(pm);
      } catch(Exception e) {}

      try {
        Intent msgIntent = new Intent(Intent.ACTION_MAIN);
        msgIntent.addCategory(Intent.CATEGORY_APP_MESSAGING);
        ResolveInfo msgInfo = pm.resolveActivity(msgIntent, PackageManager.MATCH_DEFAULT_ONLY);
        if (msgInfo != null) msgIcon = msgInfo.loadIcon(pm);
      } catch(Exception e) {}

      try {
        Intent contactsIntent = new Intent(Intent.ACTION_VIEW, ContactsContract.Contacts.CONTENT_URI);
        ResolveInfo contactsInfo = pm.resolveActivity(contactsIntent, PackageManager.MATCH_DEFAULT_ONLY);
        if (contactsInfo != null) contactsIcon = contactsInfo.loadIcon(pm);
      } catch(Exception e) {}

      try {
        musicIcon = pm.getApplicationIcon("com.mta.intertune");
        hasMusic = true;
      } catch(PackageManager.NameNotFoundException e) {}

      final Drawable finalCam = camIcon;
      final Drawable finalMsg = msgIcon;
      final Drawable finalContacts = contactsIcon;
      final Drawable finalMusic = musicIcon;
      final boolean finalHasMusic = hasMusic;

      runOnUiThread(() ->{
        if (finalCam != null)((android.widget.ImageButton) findViewById(R.id.btn_camera)).setImageDrawable(finalCam);
        if (finalMsg != null)((android.widget.ImageButton) findViewById(R.id.btn_messages)).setImageDrawable(finalMsg);
        if (finalContacts != null)((android.widget.ImageButton) findViewById(R.id.btn_contacts)).setImageDrawable(finalContacts);

        if (finalHasMusic) { ((android.widget.ImageButton) findViewById(R.id.btn_music)).setImageDrawable(finalMusic);
          findViewById(R.id.btn_music).setVisibility(View.VISIBLE);
        } else {
          findViewById(R.id.btn_music).setVisibility(View.GONE);
        }
      });
    }).start();
  }

  private void warmUpBrowser() {
    mServiceConnection = new CustomTabsServiceConnection() {@Override
      public void onCustomTabsServiceConnected(ComponentName name, CustomTabsClient client) {
        mClient = client;
        mClient.warmup(0);
        mSession = mClient.newSession(null);
        if (mSession != null) mSession.mayLaunchUrl(Uri.parse("https://www.google.com"), null, null);
      }@Override
      public void onServiceDisconnected(ComponentName name) {
        mClient = null;
        mSession = null;
      }
    };
    CustomTabsClient.bindCustomTabsService(this, "com.android.chrome", mServiceConnection);
  }

  private void setupDragToLock() {
    final View track = findViewById(R.id.lock_drag_track);
    final View handle = findViewById(R.id.lock_drag_handle);
    final View wrapper = findViewById(R.id.slider_wrapper);

    if (handle == null || track == null || wrapper == null) return;

    wrapper.setOnTouchListener(new View.OnTouchListener() {
      float dY;
      boolean hasVibrated = false; // Flag to prevent multiple vibrations
      @Override
      public boolean onTouch(View v, MotionEvent event) {
        float maxScroll = track.getHeight() - handle.getHeight();
        float threshold = maxScroll * 0.85f; // Same 85% used in ACTION_UP
        switch (event.getAction()) {
        case MotionEvent.ACTION_DOWN:
          v.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY);
          dY = handle.getY() - event.getRawY();
          hasVibrated = false; // Reset flag on new touch
          return true;

        case MotionEvent.ACTION_MOVE:
          float newY = event.getRawY() + dY;

          if (newY >= 0 && newY <= maxScroll) {
            handle.setY(newY);

            // --- VIBRATION LOGIC ---
            if (newY >= threshold && !hasVibrated) {
              // This feels like a physical 'detent' or notch
              v.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP);
              hasVibrated = true;
            } else if (newY < threshold) {
              hasVibrated = false; // Reset if they pull back up
            }
          }
          return true;

        case MotionEvent.ACTION_UP:
          if (handle.getY() > threshold) {
            lockScreen();
            handle.setY(0);
          } else {
            handle.animate().y(0).setDuration(200).setInterpolator(new android.view.animation.DecelerateInterpolator()).start();
          }
          hasVibrated = false; // Reset for next time
          return true;
        }
        return false;
      }
    });
  }

  private void setupDock() {
    android.os.Bundle instantOpen = android.app.ActivityOptions.makeCustomAnimation(this, 0, 0).toBundle();

    findViewById(R.id.btn_camera).setOnClickListener(v ->{
      try {
        Intent i = new Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(i, instantOpen);
      } catch(Exception e) {
        Intent fallback = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        startActivity(fallback, instantOpen);
      }
    });

    findViewById(R.id.btn_contacts).setOnClickListener(v ->{
      Intent i = new Intent(Intent.ACTION_VIEW, ContactsContract.Contacts.CONTENT_URI);
      startActivity(i, instantOpen);
    });

    findViewById(R.id.btn_music).setOnClickListener(v ->{
      Intent i = getPackageManager().getLaunchIntentForPackage("com.mta.intertune");
      if (i != null) startActivity(i, instantOpen);
      else Toast.makeText(this, "Music app not found", Toast.LENGTH_SHORT).show();
    });

    findViewById(R.id.btn_messages).setOnClickListener(v ->{
      Intent i = new Intent(Intent.ACTION_MAIN);
      i.addCategory(Intent.CATEGORY_APP_MESSAGING);
      try {
        startActivity(i, instantOpen);
      }
      catch(Exception e) {
        startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("sms:")), instantOpen);
      }
    });
  }

  private void setupLetterIndex() {
    LinearLayout container = findViewById(R.id.letter_index_container);
    if (container == null) return;
    container.removeAllViews();

    final List < AppModel > currentList = getVisibleApps();
    List < String > letters = new ArrayList < >();

    for (AppModel app: currentList) {
      if (app.label != null && !app.label.isEmpty()) {
        String firstChar = app.label.substring(0, 1).toUpperCase();
        if (!letters.contains(firstChar)) {
          letters.add(firstChar);
        }
      }
    }
    Collections.sort(letters);

    for (final String letter: letters) {
      TextView tv = new TextView(this);
      tv.setText(letter);
      tv.setTextColor(android.graphics.Color.parseColor("#14B8A6"));
      tv.setTextSize(16);
      tv.setTypeface(null, android.graphics.Typeface.BOLD);
      tv.setGravity(Gravity.CENTER);

      LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
      LinearLayout.LayoutParams.MATCH_PARENT, 0, 1.0f);
      tv.setLayoutParams(params);

      tv.setOnClickListener(v ->{
        v.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY);
        for (int i = 0; i < currentList.size(); i++) {
          if (currentList.get(i).label.toUpperCase().startsWith(letter)) { ((LinearLayoutManager) recyclerView.getLayoutManager()).scrollToPositionWithOffset(i, 0);
            break;
          }
        }
      });
      container.addView(tv);
    }
  }

  private List < AppModel > getVisibleApps() {
    List < AppModel > visible = new ArrayList < >();
    for (AppModel app: allApps) {
      if (!hiddenApps.contains(app.packageName)) visible.add(app);
    }
    return visible;
  }

  private void saveHomeApps() {
    try (FileOutputStream fos = openFileOutput(FAV_FILE, Context.MODE_PRIVATE)) {
      for (AppModel app: homeApps) {
        fos.write((app.packageName + "\n").getBytes());
      }
    } catch(IOException e) {
      e.printStackTrace();
    }
  }

  private void saveHiddenApps() {
    try (FileOutputStream fos = openFileOutput(HIDDEN_FILE, Context.MODE_PRIVATE)) {
      for (String pkg: hiddenApps) {
        fos.write((pkg + "\n").getBytes());
      }
    } catch(IOException e) {
      e.printStackTrace();
    }
  }

  private void loadHiddenApps() {
    hiddenApps.clear();
    File file = new File(getFilesDir(), HIDDEN_FILE);
    if (file.exists()) {
      try (BufferedReader br = new BufferedReader(new FileReader(file))) {
        String line;
        while ((line = br.readLine()) != null) hiddenApps.add(line.trim());
      } catch(IOException e) {
        e.printStackTrace();
      }
    }
  }

  private void loadAllApps() {
    new Thread(() ->{
      List < AppModel > tempList = new ArrayList < >();
      PackageManager pm = getPackageManager();
      Intent i = new Intent(Intent.ACTION_MAIN, null);
      i.addCategory(Intent.CATEGORY_LAUNCHER);
      List < ResolveInfo > activities = pm.queryIntentActivities(i, 0);

      for (ResolveInfo ri: activities) {
        AppModel app = new AppModel();
        app.label = ri.loadLabel(pm).toString();
        app.packageName = ri.activityInfo.packageName;

        tempList.add(app);
      }

      Collections.sort(tempList, (a, b) ->a.label.compareToIgnoreCase(b.label));

      runOnUiThread(() ->{
        allApps.clear();
        allApps.addAll(tempList);

        loadHomeApps();

        if (isHomeState) {
          updateAdapter(homeApps, true);
        } else {
          updateAdapter(getVisibleApps(), false);
        }
      });
    }).start();
  }

  private void loadHomeApps() {
    homeApps.clear();
    File file = new File(getFilesDir(), FAV_FILE);
    List < String > savedPackages = new ArrayList < >();
    if (file.exists()) {
      try (BufferedReader br = new BufferedReader(new FileReader(file))) {
        String line;
        while ((line = br.readLine()) != null) savedPackages.add(line.trim());
      } catch(IOException e) {
        e.printStackTrace();
      }
    }
    for (AppModel app: allApps) {
      if (savedPackages.contains(app.packageName)) homeApps.add(app);
    }
  }

  private void setupSearch() {
    // --- NEW: CUSTOM KEYBOARD WIRING ---
    View customKeyboard = findViewById(R.id.custom_keyboard);

    // 1. Block the system keyboard (Gboard) from opening for this specific search bar
    searchBar.setShowSoftInputOnFocus(false);

    // 2. Show the custom keyboard when the search bar is focused or clicked
    searchBar.setOnFocusChangeListener((v, hasFocus) ->{
      // Only show it if we are actually in the All Apps drawer
      if (hasFocus && !isHomeState) customKeyboard.setVisibility(View.VISIBLE);
    });
    searchBar.setOnClickListener(v ->{
      if (!isHomeState) customKeyboard.setVisibility(View.VISIBLE);
    });

    // 3. Create a listener that types the letter into the search bar
    View.OnClickListener keyListener = v ->{
      if (v instanceof android.widget.Button) {
        String letter = ((android.widget.Button) v).getText().toString();
        searchBar.append(letter); // This automatically triggers your TextWatcher below!
        v.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP);
      }
    };

    // 4. Attach the listener to every button in the custom keyboard
    if (customKeyboard instanceof LinearLayout) {
      LinearLayout keyboardLayout = (LinearLayout) customKeyboard;
      for (int i = 0; i < keyboardLayout.getChildCount(); i++) {
        View child = keyboardLayout.getChildAt(i);
        if (child instanceof LinearLayout) {
          LinearLayout row = (LinearLayout) child;
          for (int j = 0; j < row.getChildCount(); j++) {
            View key = row.getChildAt(j);
            if (key instanceof android.widget.Button) {
              key.setOnClickListener(keyListener);
            }
          }
        }
      }
    }

    // 5. Wire up the Delete Button
    View deleteBtn = findViewById(R.id.key_delete);
    if (deleteBtn != null) {
      deleteBtn.setOnClickListener(v ->{
        v.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP);
        android.text.Editable text = searchBar.getText();
        if (text != null && text.length() > 0) {
          text.delete(text.length() - 1, text.length());
        }
      });
    }
    // --- END CUSTOM KEYBOARD WIRING ---

    // --- YOUR EXISTING FILTER LOGIC (Do not change) ---
    searchBar.addTextChangedListener(new TextWatcher() {@Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}@Override public void onTextChanged(CharSequence s, int start, int before, int count) {}@Override
      public void afterTextChanged(Editable s) {

        // --- CRITICAL FIX: The Ghost Typist Lock ---
        if (isHomeState) return;

        String query = s.toString().trim().toLowerCase();
        if (query.isEmpty()) {
          updateAdapter(getVisibleApps(), false);
        } else {
          List < AppModel > filteredList = new ArrayList < >();
          for (AppModel app: allApps) {
            if (app.label.toLowerCase(java.util.Locale.ROOT).contains(query)) {
              filteredList.add(app);
            }
          }
          updateAdapter(filteredList, false);
        }
      }
    });

    searchBar.setOnEditorActionListener((v, actionId, event) ->{
      if (event != null && event.getAction() != KeyEvent.ACTION_DOWN) return false;

      if (actionId == EditorInfo.IME_ACTION_SEARCH || (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {

        String query = searchBar.getText().toString().trim();
        if (query.isEmpty()) return false;

        List < AppModel > filtered = new ArrayList < >();
        for (AppModel app: allApps) {
          if (app.label.toLowerCase(java.util.Locale.ROOT).contains(query.toLowerCase())) {
            filtered.add(app);
          }
        }

        if (filtered.size() > 0) {
          onLaunch(filtered.get(0));
        } else {
          Toast.makeText(MainActivity.this, "No app found", Toast.LENGTH_SHORT).show();
        }
        return true;
      }
      return false;
    });
  }

  private void setupWebSearch() {
    if (webSearchBar == null) return;

    webSearchBar.setOnFocusChangeListener((v, hasFocus) ->{
      if (hasFocus && mClient == null) {
        warmUpBrowser();
      }
    });

    webSearchBar.setOnEditorActionListener((v, actionId, event) ->{
      if (event != null && event.getAction() != KeyEvent.ACTION_DOWN) return false;

      if (actionId == EditorInfo.IME_ACTION_SEARCH || (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {

        String query = webSearchBar.getText().toString().trim();
        if (!query.isEmpty()) {
          performWebSearch(query);
        }
        return true;
      }
      return false;
    });
  }

  private void performWebSearch(String query) {
    String url = "https://www.google.com/search?q=" + Uri.encode(query) + "&ie=UTF-8";

    androidx.browser.customtabs.CustomTabsIntent.Builder builder = new androidx.browser.customtabs.CustomTabsIntent.Builder(mSession);

    builder.setShowTitle(true);
    builder.setToolbarColor(android.graphics.Color.BLACK);
    builder.setStartAnimations(this, 0, 0);
    builder.setExitAnimations(this, 0, 0);

    androidx.browser.customtabs.CustomTabsIntent customTabsIntent = builder.build();

    InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
    if (imm != null) imm.hideSoftInputFromWindow(webSearchBar.getWindowToken(), 0);

    customTabsIntent.launchUrl(this, Uri.parse(url));

    webSearchBar.setText("");
    webSearchBar.clearFocus();
  }

  private void updateAdapter(List < AppModel > list, boolean isHome) {
    int sizePx = (int)(42 * getResources().getDisplayMetrics().density);
    adapter = new AppAdapter(list, isHome, this, getPackageManager(), sizePx);
    recyclerView.setAdapter(adapter);
  }
  private void showAllApps() {
    isHomeState = false;

    // 1. Clear Focus and Hide Keyboard immediately
    if (webSearchBar != null) {
      webSearchBar.clearFocus();
      InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
      if (imm != null) imm.hideSoftInputFromWindow(webSearchBar.getWindowToken(), 0);
    }

    // 2. Ensure background takes focus so searchBar doesn't auto-blink
    findViewById(R.id.root_layout).requestFocus();

    View header = findViewById(R.id.header_container);
    View slider = findViewById(R.id.slider_wrapper);
    View letterIndex = findViewById(R.id.letter_index_container);
    View container = findViewById(R.id.all_apps_container);

    updateAdapter(getVisibleApps(), false);
    setupLetterIndex();
    if (recyclerView != null) {
      recyclerView.stopScroll(); // Kills any residual speed from the swipe-up
      recyclerView.scrollToPosition(0); // Ensures it always opens at "A"
    }
    RelativeLayout.LayoutParams lp = (RelativeLayout.LayoutParams) container.getLayoutParams();
    lp.removeRule(RelativeLayout.BELOW);
    lp.addRule(RelativeLayout.BELOW, R.id.searchBar);
    container.setLayoutParams(lp);

    header.animate().alpha(0f).setDuration(150).withEndAction(() ->header.setVisibility(View.GONE)).start();
    if (slider != null) {
      slider.animate().alpha(0f).setDuration(150).withEndAction(() ->slider.setVisibility(View.INVISIBLE)).start();
    }
    if (dockView != null) dockView.setVisibility(View.GONE);

    searchBar.setVisibility(View.VISIBLE);
    searchBar.setAlpha(0f);
    searchBar.animate().alpha(1f).setDuration(200).start();

    if (letterIndex != null) {
      letterIndex.setVisibility(View.VISIBLE);
      letterIndex.setAlpha(0f);
      letterIndex.animate().alpha(1f).setDuration(200).start();
    }

    recyclerView.setTranslationY(150f);
    recyclerView.setAlpha(0f);
    recyclerView.animate().translationY(0f).alpha(1f).setDuration(250).start();
  }
  private void showHome() {
    isHomeState = true;

    // 1. Hide System Keyboard & Clear focus
    InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
    if (searchBar != null) {
      if (imm != null) imm.hideSoftInputFromWindow(searchBar.getWindowToken(), 0);
      searchBar.clearFocus();
    }

    // 2. Clear focus from Web Search before it becomes visible
    if (webSearchBar != null) {
      webSearchBar.clearFocus();
    }

    // 3. PARK THE FOCUS ON THE BACKGROUND
    findViewById(R.id.root_layout).requestFocus();

    // 4. ANIMATE CUSTOM KEYBOARD OUT (Smoother than just GONE)
    View customKeyboard = findViewById(R.id.custom_keyboard);
    if (customKeyboard != null && customKeyboard.getVisibility() == View.VISIBLE) {
      customKeyboard.animate().alpha(0f).setDuration(150).withEndAction(() ->{
        customKeyboard.setVisibility(View.GONE);
        customKeyboard.setAlpha(1f); // Reset for next time it's shown
      }).start();
    }

    View header = findViewById(R.id.header_container);
    View slider = findViewById(R.id.slider_wrapper);
    View letterIndex = findViewById(R.id.letter_index_container);
    View container = findViewById(R.id.all_apps_container);

    // Swap back to Favorites
    updateAdapter(homeApps, true);

    // Reset RecyclerView padding (so it's not "floating" when back home)
    if (recyclerView != null) {
      recyclerView.setPadding(0, 0, 0, 0);
    }

    // Layout Rules
    RelativeLayout.LayoutParams lp = (RelativeLayout.LayoutParams) container.getLayoutParams();
    lp.removeRule(RelativeLayout.BELOW);
    lp.addRule(RelativeLayout.BELOW, R.id.header_container);
    container.setLayoutParams(lp);

    // Animations (Fade Out All Apps search)
    searchBar.animate().alpha(0f).setDuration(150).withEndAction(() ->{
      searchBar.setVisibility(View.GONE);
      searchBar.setText("");
    }).start();

    // Fade Out Letter Index
    if (letterIndex != null) {
      letterIndex.animate().alpha(0f).setDuration(150).withEndAction(() ->letterIndex.setVisibility(View.INVISIBLE)).start();
    }

    // Fade In Home Header
    header.setVisibility(View.VISIBLE);
    header.setAlpha(0f);
    header.animate().alpha(1f).setDuration(200).start();

    // Fade In Home Slider
    if (slider != null) {
      slider.setVisibility(View.VISIBLE);
      slider.setAlpha(0f);
      slider.animate().alpha(1f).setDuration(200).start();
    }

    if (dockView != null) dockView.setVisibility(View.VISIBLE);

    // Slide list into place
    recyclerView.setTranslationY( - 50f);
    recyclerView.setAlpha(0f);
    recyclerView.animate().translationY(0f).alpha(1f).setDuration(250).setInterpolator(new android.view.animation.DecelerateInterpolator()).start();
  }

  private void setupPackageReceiver() {
    packageReceiver = new android.content.BroadcastReceiver() {@Override
      public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_PACKAGE_REMOVED.equals(intent.getAction())) {
          String pkg = intent.getData().getSchemeSpecificPart();
          File file = new File(getCacheDir(), "icon_" + pkg);
          if (file.exists()) file.delete();
        }
        loadAllApps();
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
      i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_ANIMATION);
      android.os.Bundle instantOpen = android.app.ActivityOptions.makeCustomAnimation(this, 0, 0).toBundle();
      startActivity(i, instantOpen);
      overridePendingTransition(0, 0);
    }

    if (!isHomeState) {
      new android.os.Handler().postDelayed(() ->{
        if (searchBar != null) searchBar.setText("");
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null && searchBar != null) {
          imm.hideSoftInputFromWindow(searchBar.getWindowToken(), 0);
        }
      },
      100);
    }
  }

  private void lockScreen() {
    DevicePolicyManager dpm = (DevicePolicyManager) getSystemService(Context.DEVICE_POLICY_SERVICE);
    ComponentName adminComponent = new ComponentName(this, MyAdminReceiver.class);

    if (dpm.isAdminActive(adminComponent)) {
      dpm.lockNow();
    } else {
      Intent intent = new Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN);
      intent.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent);
      intent.putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "Needed to lock the screen via gesture.");
      startActivity(intent);
    }
  }

  @Override
  public void onLongPress(AppModel app) {
    getWindow().getDecorView().performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS);

    if (isHomeState) {
      new AlertDialog.Builder(this, AlertDialog.THEME_DEVICE_DEFAULT_DARK).setTitle("Remove " + app.label + "?").setPositiveButton("Yes", (d, w) ->{
        homeApps.remove(app);
        saveHomeApps();
        updateAdapter(homeApps, true);
      }).setNegativeButton("No", null).show();
    } else {
      boolean isHidden = hiddenApps.contains(app.packageName);
      String[] options = {
        "Add to Home",
        isHidden ? "Unhide": "Hide",
        "Uninstall"
      };

      new AlertDialog.Builder(this, AlertDialog.THEME_DEVICE_DEFAULT_DARK).setTitle(app.label.toUpperCase()).setItems(options, (dialog, which) ->{
        if (which == 0) {
          boolean exists = false;
          for (AppModel h: homeApps) if (h.packageName.equals(app.packageName)) exists = true;
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
          // Uninstalling...
          Intent intent = new Intent(Intent.ACTION_DELETE, Uri.parse("package:" + app.packageName));
          // Remove FLAG_ACTIVITY_NEW_TASK so it stays within the current flow if possible
          startActivity(intent);
        }
      }).show();
    }
  }

  @Override
  public void onResume() {
    super.onResume();

    // Refresh the clock
    handler.removeCallbacks(clockRunnable);
    handler.post(clockRunnable);

    // Keep focus management
    if (webSearchBar != null) webSearchBar.clearFocus();
    findViewById(R.id.root_layout).requestFocus();

    // Refresh the app list (in case something was uninstalled)
    loadAllApps();

    // FIX: Only call showHome() if we were actually on the home screen
    // If isHomeState is false, it means we were in the App Drawer, so STAY THERE.
    if (isHomeState) {
      showHome();
    } else {
      // If we are in the App Drawer, just make sure the custom keyboard is 
      // hidden so it doesn't overlap the uninstall confirmation
      findViewById(R.id.custom_keyboard).setVisibility(View.GONE);
    }
  }@Override
  public void onBackPressed() {
    if (!isHomeState) {
      // If the drawer is open, "slide it down" to go home
      if (searchBar.getText().length() > 0) {
        searchBar.setText(""); // Clear search first if typing
      } else {
        showHome();
      }
    } else {
      // Do nothing on home screen to prevent accidental exit
      // Or call super.onBackPressed() if you want it to minimize
    }
  }@Override
  protected void onPause() {
    super.onPause();
    // 1. Stop the clock to save battery/CPU
    handler.removeCallbacks(clockRunnable);

    // 2. Hide keyboard if it was open
    InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
    if (imm != null && searchBar != null) {
      imm.hideSoftInputFromWindow(searchBar.getWindowToken(), 0);
    }
  }@Override
  protected void onDestroy() {
    super.onDestroy();
    handler.removeCallbacksAndMessages(null);

    if (packageReceiver != null) {
      try {
        unregisterReceiver(packageReceiver);
      } catch(Exception e) {}
    }

    // UNBIND THE BROWSER SERVICE
    if (mServiceConnection != null) {
      unbindService(mServiceConnection);
      mClient = null;
      mSession = null;
    }
  }
}