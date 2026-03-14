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

public class AppAdapter extends RecyclerView.Adapter<AppAdapter.ViewHolder> {

    private static LruCache<String, Bitmap> ramCache;
    private final ExecutorService executorService = Executors.newFixedThreadPool(2);
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private final List<AppModel> appList;
    private final OnAppActionListener actionListener;
    private final int sizePx;
    private final PackageManager pm;
    private final Context context;

    public interface OnAppActionListener {
        void onLaunch(AppModel app);
        void onLongPress(AppModel app);
    }

    public AppAdapter(List<AppModel> list, boolean isHome, OnAppActionListener listener, PackageManager pm, int sizePx) {
        this.appList = list;
        this.actionListener = listener;
        this.pm = pm;
        this.sizePx = sizePx;
        this.context = ((Context) listener);

        if (ramCache == null) {
            int cacheSize = (int) (Runtime.getRuntime().maxMemory() / 1024 / 10);
            ramCache = new LruCache<String, Bitmap>(cacheSize) {
                @Override
                protected int sizeOf(String key, Bitmap bitmap) {
                    return bitmap.getByteCount() / 1024;
                }
            };
        }
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        TextView tv = new TextView(parent.getContext());
        int height = (int) (52 * parent.getContext().getResources().getDisplayMetrics().density);
        tv.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, height));
        tv.setTextColor(Color.WHITE);
        tv.setGravity(Gravity.CENTER_VERTICAL);
        tv.setPadding(40, 0, 40, 0);
        return new ViewHolder(tv);
    }

   @Override
public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
    AppModel app = appList.get(position);
    String pkg = app.packageName;

    // 1. Basic Text setup
    holder.tv.setText(app.label.toLowerCase());
    holder.tv.setTextSize(17);
    holder.tv.setTypeface(null, Typeface.BOLD);
    holder.tv.setCompoundDrawablePadding(24);
    holder.tv.setTag(pkg);

    // 2. THE SPEED FIX: Check the Pre-loaded RAM Cache first
    // This is the iconCache we created in MainActivity
    Drawable preLoadedIcon = MainActivity.iconCache.get(pkg);

    if (preLoadedIcon != null) {
        // If it exists in RAM, show it instantly. No threads, no lag.
        preLoadedIcon.setBounds(0, 0, sizePx, sizePx);
        holder.tv.setCompoundDrawables(preLoadedIcon, null, null, null);
    } else {
        // 3. Fallback: If not in cache, use your background loader
        holder.tv.setCompoundDrawables(null, null, null, null);
        loadAppIcon(holder.tv, app);
    }

    // 4. Listeners
    holder.itemView.setOnClickListener(v -> actionListener.onLaunch(app));
    holder.tv.setOnLongClickListener(v -> {
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

        executorService.execute(() -> {
            // 1. Try to load from DISK first (Instant after first run)
            Bitmap bitmap = loadFromDisk(pkg);

            if (bitmap == null) {
                // 2. If not on disk, extract from SYSTEM (Slowest part)
                bitmap = extractFromSystem(pkg);
                if (bitmap != null) {
                    saveToDisk(pkg, bitmap);
                }
            }

            if (bitmap != null) {
                ramCache.put(pkg, bitmap);
                final Bitmap finalBitmap = bitmap;
                mainHandler.post(() -> {
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
            Bitmap bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.RGB_565);
            Canvas canvas = new Canvas(bitmap);
            drawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
            drawable.draw(canvas);
            return bitmap;
        } catch (Exception e) { return null; }
    }

    private void saveToDisk(String pkg, Bitmap bitmap) {
        File file = new File(context.getCacheDir(), "icon_" + pkg);
        try (FileOutputStream out = new FileOutputStream(file)) {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
        } catch (Exception e) { e.printStackTrace(); }
    }

    private Bitmap loadFromDisk(String pkg) {
        File file = new File(context.getCacheDir(), "icon_" + pkg);
        if (file.exists()) {
            return BitmapFactory.decodeFile(file.getAbsolutePath());
        }
        return null;
    }

    private void applyIcon(TextView tv, Bitmap bitmap) {
        BitmapDrawable drawable = new BitmapDrawable(tv.getResources(), bitmap);
        drawable.setBounds(0, 0, sizePx, sizePx);
        tv.setCompoundDrawables(drawable, null, null, null);
    }

    @Override
    public int getItemCount() { return appList.size(); }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tv;
        ViewHolder(TextView v) { super(v); tv = v; }
    }
}