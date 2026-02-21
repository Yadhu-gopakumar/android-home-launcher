package com.minimy.minimy;

import android.content.pm.PackageManager;
import android.view.ViewGroup;
import android.widget.TextView;
import android.graphics.Color;
import android.view.Gravity; // Added for perfect alignment
import androidx.recyclerview.widget.RecyclerView;
import java.util.List;

public class AppAdapter extends RecyclerView.Adapter<AppAdapter.ViewHolder> {
    private List<AppModel> appList;
    private OnAppActionListener actionListener;
    private boolean isHome;
    
    private int sizePx = -1; 
    private PackageManager pm;

    public interface OnAppActionListener {
        void onLaunch(AppModel app);
        void onLongPress(AppModel app);
    }

    public AppAdapter(List<AppModel> list, boolean isHome, OnAppActionListener listener) {
        this.appList = list;
        this.isHome = isHome;
        this.actionListener = listener;
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        TextView tv = new TextView(parent.getContext());
        tv.setLayoutParams(new ViewGroup.LayoutParams(-1, -2));
        tv.setTextColor(Color.WHITE);
        tv.setTextSize(18);
        
        // --- ALIGNMENT FIX ---
        // Forces the text to sit exactly in the middle of the icon vertically
        tv.setGravity(Gravity.CENTER_VERTICAL); 
        
        return new ViewHolder(tv);
    }

    @Override
    public void onBindViewHolder(ViewHolder holder, int position) {
        AppModel app = appList.get(position);
        holder.tv.setTypeface(null, android.graphics.Typeface.BOLD);
        holder.tv.setText(app.label);
        holder.tv.setBackgroundResource(0); 
        holder.tv.setTextColor(Color.WHITE); 

        RecyclerView.LayoutParams params = new RecyclerView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        holder.tv.setLayoutParams(params);
        
        // --- GAP FIX ---
        if (isHome) {
            // Reduced from 35 to 22 for a tighter, cleaner Home screen look
            holder.tv.setPadding(60, 22, 40, 22);
        } else {
            holder.tv.setPadding(60, 15, 40, 15);
        }

        if (sizePx == -1) {
            sizePx = (int) (42 * holder.tv.getContext().getResources().getDisplayMetrics().density);
            pm = holder.tv.getContext().getPackageManager();
        }

        if (app.icon == null) {
            try {
                app.icon = pm.getApplicationIcon(app.packageName);
                app.icon.setBounds(0, 0, sizePx, sizePx);
            } catch (Exception e) {
                // Ignore
            }
        }

        if (app.icon != null) {
            holder.tv.setCompoundDrawables(app.icon, null, null, null);
        } else {
            holder.tv.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0);
        }
        
        holder.tv.setCompoundDrawablePadding(50);

        holder.tv.setOnClickListener(v -> actionListener.onLaunch(app));
        holder.tv.setOnLongClickListener(v -> {
            actionListener.onLongPress(app);
            return true;
        });
    }

    public AppModel getAppAt(int position) { return appList.get(position); }
    @Override
    public int getItemCount() { return appList.size(); }
    
    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tv;
        ViewHolder(TextView v) { super(v); tv = v; }
    }
}
