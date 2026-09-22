/*
 * Copyright (c) 2026
 * SPDX-License-Identifier: GPL-2.0-or-later
 *
 * Per-app VPN picker: lets the user choose which installed apps should
 * have their traffic routed through the VPN tunnel (whitelist only).
 */

package app.openconnect;

import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import com.google.android.material.materialswitch.MaterialSwitch;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class AppSelectActivity extends ToolbarActivity {

    /** Comma-separated list of currently selected package names (in). */
    public static final String EXTRA_SELECTED = "io.pengyue.oconnect.APP_SELECT_SELECTED";
    /** Comma-separated list of selected package names (out, via setResult). */
    public static final String EXTRA_RESULT = "io.pengyue.oconnect.APP_SELECT_RESULT";

    private static class AppEntry implements Comparable<AppEntry> {
        String label;
        String packageName;
        Drawable icon;
        boolean checked;

        @Override
        public int compareTo(AppEntry other) {
            return label.toLowerCase(Locale.getDefault())
                    .compareTo(other.label.toLowerCase(Locale.getDefault()));
        }
    }

    private final List<AppEntry> mAllApps = new ArrayList<>();
    private final List<AppEntry> mLaunchableApps = new ArrayList<>();
    private final Set<String> mSelected = new HashSet<>();

    private final Set<String> mInitialSelected = new HashSet<>();
    private View mWarningBanner;

    private ListView mListView;
    private EditText mSearchBox;
    private MaterialSwitch mShowSystemSwitch;
    private AppAdapter mAdapter;
    private String mSearchQuery = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        android.content.SharedPreferences appSp = getSharedPreferences("app_settings", MODE_PRIVATE);
        String lang = appSp.getString("app_lang", "ru");
        java.util.Locale locale = new java.util.Locale(lang);
        java.util.Locale.setDefault(locale);
        android.content.res.Configuration config = new android.content.res.Configuration();
        config.setLocale(locale);
        getResources().updateConfiguration(config, getResources().getDisplayMetrics());
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_app_select);
        setupToolbar(R.id.toolbar, getString(R.string.app_select_title), true);

        String selected = getIntent().getStringExtra(EXTRA_SELECTED);
        if (selected != null) {
            for (String pkg : selected.split(",")) {
                pkg = pkg.trim();
                if (!pkg.isEmpty()) {
                    mSelected.add(pkg);
                    mInitialSelected.add(pkg);
                }
            }
        }
        mWarningBanner = findViewById(R.id.panel_reconnect_warning);
        mListView = findViewById(R.id.app_select_list);
        mSearchBox = findViewById(R.id.app_select_search);
        mShowSystemSwitch = findViewById(R.id.app_select_show_system_switch);

        mAdapter = new AppAdapter();
        mListView.setAdapter(mAdapter);
        mListView.setOnItemClickListener((parent, view, position, id) -> {
            AppEntry entry = mAdapter.getItem(position);
            entry.checked = !entry.checked;
            if (entry.checked) {
                mSelected.add(entry.packageName);
            } else {
                mSelected.remove(entry.packageName);
            }
            mAdapter.notifyDataSetChanged();
            updateWarningBanner(); 
        });

        mSearchBox.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override
            public void onTextChanged(CharSequence s, int a, int b, int c) {}
            @Override
            public void afterTextChanged(Editable s) {
                mSearchQuery = s.toString();
                applyFilter();
            }
        });

        mShowSystemSwitch.setOnCheckedChangeListener((btn, checked) -> applyFilter());

        loadApps();
    }

    @Override
    public void onBackPressed() {
        finishWithResult();
    }

    /* the toolbar's back arrow (see ToolbarActivity) also just calls finish();
       override it so it saves the current selection instead of discarding it */
    @Override
    public void finish() {
        if (!isFinishing()) {
            finishWithResult();
        } else {
            super.finish();
        }
    }

    private void finishWithResult() {
        StringBuilder sb = new StringBuilder();
        for (String pkg : mSelected) {
            if (sb.length() > 0) {
                sb.append(",");
            }
            sb.append(pkg);
        }
        Intent data = new Intent();
        data.putExtra(EXTRA_RESULT, sb.toString());
        setResult(RESULT_OK, data);
        super.finish();
    }

    private void loadApps() {
        new Thread(() -> {
            PackageManager pm = getPackageManager();
            List<ApplicationInfo> installed = pm.getInstalledApplications(PackageManager.GET_META_DATA);
            String myPackage = getPackageName();

            Set<String> launchablePackages = new HashSet<>();
            Intent launcherIntent = new Intent(Intent.ACTION_MAIN);
            launcherIntent.addCategory(Intent.CATEGORY_LAUNCHER);
            List<ResolveInfo> launchable = pm.queryIntentActivities(launcherIntent, 0);
            for (ResolveInfo ri : launchable) {
                launchablePackages.add(ri.activityInfo.packageName);
            }

            List<AppEntry> all = new ArrayList<>();
            List<AppEntry> withLauncher = new ArrayList<>();
            for (ApplicationInfo ai : installed) {
                if (ai.packageName.equals(myPackage)) {
                    continue;
                }
                AppEntry entry = new AppEntry();
                entry.packageName = ai.packageName;
                entry.label = String.valueOf(pm.getApplicationLabel(ai));
                entry.icon = pm.getApplicationIcon(ai);
                entry.checked = mSelected.contains(ai.packageName);
                all.add(entry);
                if (launchablePackages.contains(ai.packageName)) {
                    withLauncher.add(entry);
                }
            }
            Collections.sort(all);
            Collections.sort(withLauncher);

            runOnUiThread(() -> {
                mAllApps.clear();
                mAllApps.addAll(all);
                mLaunchableApps.clear();
                mLaunchableApps.addAll(withLauncher);
                applyFilter();
            });
        }, "AppSelectLoader").start();
    }

    private void updateWarningBanner() {
        if (mWarningBanner == null) return;
        boolean changed = !mSelected.equals(mInitialSelected);
        mWarningBanner.setVisibility(changed ? View.VISIBLE : View.GONE);
    }

    private void applyFilter() {
        List<AppEntry> source = mShowSystemSwitch.isChecked() ? mAllApps : mLaunchableApps;
        String query = mSearchQuery.trim().toLowerCase(Locale.getDefault());
        List<AppEntry> filtered = new ArrayList<>();
        for (AppEntry entry : source) {
            if (query.isEmpty()
                    || entry.label.toLowerCase(Locale.getDefault()).contains(query)
                    || entry.packageName.toLowerCase(Locale.getDefault()).contains(query)) {
                filtered.add(entry);
            }
        }
        mAdapter.setData(filtered);
    }

    private class AppAdapter extends BaseAdapter {
        private List<AppEntry> mData = new ArrayList<>();

        void setData(List<AppEntry> data) {
            mData = data;
            notifyDataSetChanged();
        }

        @Override
        public int getCount() {
            return mData.size();
        }

        @Override
        public AppEntry getItem(int position) {
            return mData.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = LayoutInflater.from(AppSelectActivity.this)
                        .inflate(R.layout.app_select_list_item, parent, false);
            }
            AppEntry entry = mData.get(position);

            ImageView icon = convertView.findViewById(R.id.app_item_icon);
            TextView title = convertView.findViewById(R.id.app_item_title);
            TextView pkg = convertView.findViewById(R.id.app_item_package);
            MaterialSwitch sw = convertView.findViewById(R.id.app_item_switch);

            icon.setImageDrawable(entry.icon);
            title.setText(entry.label);
            pkg.setText(entry.packageName);
            sw.setChecked(entry.checked);

            return convertView;
        }
    }
}
