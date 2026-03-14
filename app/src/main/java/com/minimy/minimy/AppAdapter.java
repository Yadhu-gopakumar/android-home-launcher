package com.minimy.minimy;

import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.io.File;
import java.io.FileOutputStream;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AppAdapter extends RecyclerView.Adapter < AppAdapter.ViewHolder > {

  // 1. FIX: Made static so they are shared and don't spawn infinite threads on keystrokes
  private static LruCache < String,
  Bitmap > ramCache;
  private static ExecutorService executorService;
  private static Handler mainHandler;

  private final List < AppModel > appList;
  private final OnAppActionListener actionListener;
  private final int sizePx;
  private final PackageManager pm;
  private final Context appContext; // 2. FIX: Application context to prevent memory leaks
  public interface OnAppActionListener {
    void onLaunch(AppModel app);
    void onLongPress(AppModel app);
  }

  public AppAdapter(List < AppModel > list, boolean isHome, OnAppActionListener listener, PackageManager pm, int sizePx) {
    this.appList = list;
    this.actionListener = listener;
    this.pm = pm;
    this.sizePx = sizePx;
    this.appContext = ((Context) listener).getApplicationContext(); // Safe context
    // Initialize statics exactly once
    if (ramCache == null) {
      int cacheSize = (int)(Runtime.getRuntime().maxMemory() / 1024 / 10);
      ramCache = new LruCache < String,
      Bitmap > (cacheSize) {@Override
        protected int sizeOf(String key, Bitmap bitmap) {
          return bitmap.getByteCount() / 1024;
        }
      };
    }
    if (executorService == null) {
      // 3 threads prevent the "traffic jam" when scrolling fast
      executorService = Executors.newFixedThreadPool(3, r ->{
        Thread t = new Thread(r);
        t.setPriority(Thread.MIN_PRIORITY); // Still keeps UI smooth
        return t;
      });
    }
    if (mainHandler == null) {
      mainHandler = new Handler(Looper.getMainLooper());
    }
  }

  @NonNull@Override
  public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
    TextView tv = new TextView(parent.getContext());
    int height = (int)(52 * parent.getContext().getResources().getDisplayMetrics().density);
    tv.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, height));
    tv.setTextColor(android.graphics.Color.parseColor("#F3F4F6"));
    tv.setGravity(Gravity.CENTER_VERTICAL);
    tv.setPadding(40, 0, 40, 0);
    return new ViewHolder(tv);
  }

  @Override
  public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
    AppModel app = appList.get(position);
    String pkg = app.packageName;

    holder.tv.setText(app.label.toLowerCase(java.util.Locale.ROOT));
    holder.tv.setTextSize(17);
    holder.tv.setTypeface(null, Typeface.BOLD);
    holder.tv.setCompoundDrawablePadding(24);
    holder.tv.setTag(pkg);

    // Clear the old icon immediately so recycled views don't flash old icons
    holder.tv.setCompoundDrawables(null, null, null, null);

    // Always use your optimized pipeline
    loadAppIcon(holder.tv, app);

    holder.itemView.setOnClickListener(v ->actionListener.onLaunch(app));
    holder.tv.setOnLongClickListener(v ->{
      actionListener.onLongPress(app);
      return true;
    });
  }
  private void loadAppIcon(final TextView tv, final AppModel app) {
    final String pkg = app.packageName;
    Bitmap cached = ramCache.get(pkg);

    if (cached != null) {
      applyIcon(tv, cached);
      return;
    }

    executorService.execute(() ->{
      Bitmap bitmap = loadFromDisk(pkg);

      if (bitmap == null) {
        bitmap = extractFromSystem(pkg);
        if (bitmap != null) {
          saveToDisk(pkg, bitmap);
        }
      }

      if (bitmap != null) {
        ramCache.put(pkg, bitmap);
        final Bitmap finalBitmap = bitmap;
        mainHandler.post(() ->{
          if (tv.getTag().equals(pkg)) {
            applyIcon(tv, finalBitmap);
          }
        });
      }
    });
  }

  private Bitmap extractFromSystem(String pkg) {
    try {
      Drawable drawable = pm.getApplicationIcon(pkg);
      Bitmap bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888);
      bitmap.setDensity(appContext.getResources().getDisplayMetrics().densityDpi); // Force match
      Canvas canvas = new Canvas(bitmap);
      drawable.setBounds(0, 0, sizePx, sizePx);
      drawable.draw(canvas);
      return bitmap;
    } catch(Exception e) {
      return null;
    }
  }
  private void saveToDisk(String pkg, Bitmap bitmap) {
    File file = new File(appContext.getCacheDir(), "icon_" + pkg);
    try (FileOutputStream out = new FileOutputStream(file)) {
      // Use LOSSY for icons to maximize speed and minimize storage wear
      if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
        bitmap.compress(Bitmap.CompressFormat.WEBP_LOSSY, 75, out);
      } else {
        bitmap.compress(Bitmap.CompressFormat.WEBP, 75, out);
      }
    } catch(Exception e) {
      e.printStackTrace();
    }
  }

  private Bitmap loadFromDisk(String pkg) {
    File file = new File(appContext.getCacheDir(), "icon_" + pkg);
    if (file.exists()) {
      BitmapFactory.Options options = new BitmapFactory.Options();
      // This makes the decoded bitmap take up 50% less RAM than the default
      options.inPreferredConfig = Bitmap.Config.ARGB_8888;
      return BitmapFactory.decodeFile(file.getAbsolutePath(), options);
    }
    return null;
  }

  private void applyIcon(TextView tv, Bitmap bitmap) {
    BitmapDrawable drawable = new BitmapDrawable(tv.getResources(), bitmap);
    drawable.setBounds(0, 0, sizePx, sizePx);
    tv.setCompoundDrawables(drawable, null, null, null);
  }

  @Override
  public int getItemCount() {
    return appList.size();
  }

  static class ViewHolder extends RecyclerView.ViewHolder {
    TextView tv;
    ViewHolder(TextView v) {
      super(v);
      tv = v;
    }
  }
}