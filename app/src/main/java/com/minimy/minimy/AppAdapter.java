package com.minimy.minimy;

import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AppAdapter extends RecyclerView.Adapter<AppAdapter.ViewHolder> {

    // --- INDUSTRY LEVEL CACHE ---
    // This stays in RAM so icons load instantly during scroll
    private static final Map<String, Drawable> iconCache = new HashMap<>();

    private final List<AppModel> appList;
    private final OnAppActionListener actionListener;
    private final boolean isHome;
    private final int sizePx;
    private final PackageManager pm;

    public interface OnAppActionListener {

        void onLaunch(AppModel app);

        void onLongPress(AppModel app);
    }

    public AppAdapter(List<AppModel> list, boolean isHome, OnAppActionListener listener, PackageManager pm, int sizePx) {
        this.appList = list;
        this.isHome = isHome;
        this.actionListener = listener;
        this.pm = pm;
        this.sizePx = sizePx;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        TextView tv = new TextView(parent.getContext());

        // 1. ULTR-COMPACT ROW HEIGHT
        // 48dp is the "Gold Standard" for mobile touch targets. 
        // It's the smallest height that remains easy to tap.
        int height = (int) (48 * parent.getContext().getResources().getDisplayMetrics().density);
        tv.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, height));

        tv.setTextColor(Color.WHITE);

        // 2. REDUCED TEXT SIZE
        // Dropping from 17sp to 15sp for a cleaner, more sophisticated look.
        tv.setTextSize(15);

        tv.setGravity(Gravity.CENTER_VERTICAL);
        tv.setTypeface(null, Typeface.BOLD);

        // Keep left padding for the icon alignment
        tv.setPadding(40, 0, 40, 0);

        return new ViewHolder(tv);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        AppModel app = appList.get(position);
        holder.tv.setText(app.label.toLowerCase());

        int iconResId = getCategoryIcon(app);
        try {
            Drawable icon = androidx.appcompat.content.res.AppCompatResources.getDrawable(holder.tv.getContext(), iconResId);
            if (icon != null) {
                // 3. SCALED ICON FOR SMALLER ROW
                // 38dp keeps the icon prominent without touching the top/bottom edges of the 48dp row.
                int iconSize = (int) (38 * holder.tv.getContext().getResources().getDisplayMetrics().density);
                icon.setBounds(0, 0, iconSize, iconSize);
                holder.tv.setCompoundDrawables(icon, null, null, null);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        // 4. TIGHTER HORIZONTAL GAP
        holder.tv.setCompoundDrawablePadding(20);

        holder.tv.setOnClickListener(v -> actionListener.onLaunch(app));
        holder.tv.setOnLongClickListener(v -> {
            actionListener.onLongPress(app);
            return true;
        });
    }

    private int getCategoryIcon(AppModel app) {
        String pkg = app.packageName.toLowerCase();

        // --- Direct Social & Communication Matches ---
        if (pkg.contains("whatsapp")) {
            return R.drawable.ic_whatsapp;
        }
        if (pkg.contains("facebook") || pkg.contains("fb")) {
            return R.drawable.ic_fb;
        }
        if (pkg.contains("telegram")) {
            return R.drawable.ic_telegram;
        }
        if (pkg.contains("instagram") || pkg.contains(".x.")) {
            return R.drawable.ic_x;
        }
        if (pkg.contains("linkedin") || pkg.contains("linkdin")) {
            return R.drawable.ic_linkdin;
        }
        if (pkg.contains("pinterst")) {
            return R.drawable.ic_pinterst;
        }

        // --- Google & Productivity ---
        if (pkg.contains("com.google.android.gm")) {
            return R.drawable.ic_gmail;
        }
        if (pkg.contains("chrome")) {
            return R.drawable.ic_chrome;
        }
        if (pkg.contains("drive")) {
            return R.drawable.ic_drive;
        }
        if (pkg.contains("google.android.apps.photos") || pkg.contains("gallery")) {
            return R.drawable.ic_photos;
        }
        if (pkg.contains("gmap") || pkg.contains("maps")) {
            return R.drawable.ic_gmap;
        }
        if (pkg.contains("googlequicksearchbox")) {
            return R.drawable.ic_google;
        }
        if (pkg.contains("playstore") || pkg.contains("vending")) {
            return R.drawable.ic_playstore;
        }
        if (pkg.contains("googleapps")) {
            return R.drawable.ic_googleapps;
        }

        // --- AI & Development ---
        if (pkg.contains("chatgpt")) {
            return R.drawable.ic_chatgpt;
        }
        if (pkg.contains("deepseek")) {
            return R.drawable.ic_deepseek;
        }
        if (pkg.contains("gemini")) {
            return R.drawable.ic_gemini;
        }
        if (pkg.contains("github")) {
            return R.drawable.ic_github;
        }
        if (pkg.contains("flutter")) {
            return R.drawable.ic_flutter;
        }

        // --- System & Utilities ---
        if (pkg.contains("settings")) {
            return R.drawable.ic_settings;
        }
        if (pkg.contains("security") || pkg.contains("safecenter")) {
            return R.drawable.ic_security;
        }
        if (pkg.contains("camera")) {
            return R.drawable.ic_camera;
        }
        if (pkg.contains("calculator")) {
            return R.drawable.ic_calculator;
        }
        if (pkg.contains("calendar")) {
            return R.drawable.ic_calnder;
        }
        if (pkg.contains("clock") || pkg.contains("alarm")) {
            return R.drawable.ic_clock;
        }
        if (pkg.contains("weather")) {
            return R.drawable.ic_weather;
        }
        if (pkg.contains("filemanager") || pkg.contains("documentsui")) {
            return R.drawable.ic_filemanager;
        }
        if (pkg.contains("phone") || pkg.contains("dialer")) {
            return R.drawable.ic_phone;
        }
        if (pkg.contains("contact")) {
            return R.drawable.ic_contacts;
        }
        if (pkg.contains("message") || pkg.contains("mms")) {
            return R.drawable.ic_message;
        }

        // --- Media & Entertainment ---
        if (pkg.contains("youtube")) {
            return R.drawable.ic_youtube;
        }
        if (pkg.contains("vlc")) {
            return R.drawable.ic_vlc;
        }
        if (pkg.contains("spotify") || pkg.contains("music") || pkg.contains("intertune")) {
            return R.drawable.ic_music;
        }
        if (pkg.contains("video")) {
            return R.drawable.ic_video;
        }
        if (pkg.contains("game")) {
            return R.drawable.ic_game;
        }

        // --- Other Categories ---
        if (pkg.contains("upi") || pkg.contains("paisa")) {
            return R.drawable.ic_upi;
        }
        if (pkg.contains("pdf")) {
            return R.drawable.ic_pdf;
        }
        if (pkg.contains("notes")) {
            return R.drawable.ic_notes;
        }
        if (pkg.contains("browser")) {
            return R.drawable.ic_browsers;
        }

        // Final Fallbacks
        if (pkg.contains("android")) {
            return R.drawable.ic_android;
        }
        return R.drawable.ic_default_apps;
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
