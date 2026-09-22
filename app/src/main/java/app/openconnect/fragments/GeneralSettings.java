/*
 * Adapted from OpenVPN for Android
 * Copyright (c) 2012-2013, Arne Schwabe
 * All rights reserved.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301,
 * USA.
 *
 * In addition, as a special exception, the copyright holders give
 * permission to link the code of portions of this program with the
 * OpenSSL library.
 */
package app.openconnect.fragments;

import java.util.Locale;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.os.Bundle;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.view.View;

import app.openconnect.FragActivity;
import app.openconnect.PreferenceScreenStyler;
import app.openconnect.R;

public class GeneralSettings extends PreferenceFragment
        implements OnSharedPreferenceChangeListener {

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Привязываем SharedPreferences, где лежит "app_settings"
        getPreferenceManager().setSharedPreferencesName("app_settings");
        getPreferenceManager().setSharedPreferencesMode(Context.MODE_PRIVATE);

        addPreferencesFromResource(R.xml.general_settings);

        initVersionPref();
        initAboutPref();
        updateLanguageSummary();
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        PreferenceScreenStyler.apply(this);
    }

    private void initVersionPref() {
        Preference versionPref = findPreference("app_version");
        if (versionPref != null) {
            try {
                PackageInfo pInfo = getActivity().getPackageManager()
                        .getPackageInfo(getActivity().getPackageName(), 0);
                versionPref.setSummary(pInfo.versionName);
            } catch (PackageManager.NameNotFoundException e) {
                versionPref.setSummary("1.0.0");
            }
        }
    }

    private void initAboutPref() {
        Preference aboutPref = findPreference("about_app");
        if (aboutPref != null) {
            aboutPref.setOnPreferenceClickListener(preference -> {
                Intent intent = new Intent(getActivity(), FragActivity.class);
                intent.putExtra(FragActivity.EXTRA_FRAGMENT_NAME, "AboutFragment");
                startActivity(intent);
                return true;
            });
        }
    }

    private void updateLanguageSummary() {
        ListPreference langPref = (ListPreference) findPreference("app_lang");
        if (langPref != null) {
            langPref.setSummary(langPref.getEntry());
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        getPreferenceScreen().getSharedPreferences()
                .registerOnSharedPreferenceChangeListener(this);
    }

    @Override
    public void onPause() {
        super.onPause();
        getPreferenceScreen().getSharedPreferences()
                .unregisterOnSharedPreferenceChangeListener(this);
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sp, String key) {
        if ("app_lang".equals(key)) {
            updateLanguageSummary();
            String newLang = sp.getString("app_lang", "ru");

            // Применяем локаль в системе
            Locale locale = new Locale(newLang);
            Locale.setDefault(locale);
            Configuration config = new Configuration();
            config.setLocale(locale);
            getActivity().getResources().updateConfiguration(config, 
                    getActivity().getResources().getDisplayMetrics());

            // Мгновенный перезапуск для перерисовки всех текстов
            Intent intent = getActivity().getBaseContext().getPackageManager()
                    .getLaunchIntentForPackage(getActivity().getBaseContext().getPackageName());
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            getActivity().finish();
        }
    }
}
