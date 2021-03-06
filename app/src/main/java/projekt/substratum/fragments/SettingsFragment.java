/*
 * Copyright (c) 2016-2017 Projekt Substratum
 * This file is part of Substratum.
 *
 * Substratum is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Substratum is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Substratum.  If not, see <http://www.gnu.org/licenses/>.
 */

package projekt.substratum.fragments;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.support.design.widget.Snackbar;
import android.support.v7.preference.CheckBoxPreference;
import android.support.v7.preference.Preference;
import android.support.v7.preference.PreferenceFragmentCompat;
import android.util.Log;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;

import projekt.substratum.BuildConfig;
import projekt.substratum.LauncherActivity;
import projekt.substratum.R;
import projekt.substratum.config.References;
import projekt.substratum.util.AOPTCheck;
import projekt.substratum.util.ReadSupportedROMsFile;
import projekt.substratum.util.SheetDialog;

public class SettingsFragment extends PreferenceFragmentCompat {

    private ProgressDialog mProgressDialog;
    private StringBuilder platformSummary;
    private Preference systemPlatform;
    private int tapCount = 0;

    private boolean checkSettingsPackageSupport() {
        try {
            Resources res = getContext().getPackageManager()
                    .getResourcesForApplication(References.settingsPackageName);
            int substratum_icon = res.getIdentifier(References.settingsPackageName + ":drawable/" +
                            References.settingsSubstratumDrawableName, "drawable",
                    References.settingsPackageName);
            return substratum_icon != 0;
        } catch (Exception e) {
            Log.e(References.SUBSTRATUM_LOG, "Could not load drawable from Settings.apk.");
        }
        return false;
    }

    @SuppressWarnings("StringConcatenationInsideStringBufferAppend")
    @Override
    public void onCreatePreferences(Bundle bundle, String s) {
        if (References.checkOMS(getContext())) {
            addPreferencesFromResource(R.xml.preference_fragment);
        } else {
            addPreferencesFromResource(R.xml.legacy_preference_fragment);
        }

        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(
                getContext());

        Preference aboutSubstratum = getPreferenceManager().findPreference
                ("about_substratum");
        String aboutSubstratumSummary = BuildConfig.VERSION_NAME + " (" + BuildConfig
                .VERSION_CODE + ")";
        if (BuildConfig.DEBUG) {
            aboutSubstratumSummary = aboutSubstratumSummary + " - " + BuildConfig.GIT_HASH;
        }
        aboutSubstratum.setSummary(aboutSubstratumSummary);
        aboutSubstratum.setIcon(getContext().getDrawable(R.mipmap.main_launcher));
        aboutSubstratum.setOnPreferenceClickListener(
                preference -> {
                    try {
                        String sourceURL = getString(R.string.substratum_github);
                        Intent i = new Intent(Intent.ACTION_VIEW);
                        i.setData(Uri.parse(sourceURL));
                        startActivity(i);
                    } catch (ActivityNotFoundException activityNotFoundException) {
                        //
                    }
                    return false;
                });

        systemPlatform = getPreferenceManager().findPreference
                ("system_platform");

        /*
        if (References.checkOMS(getContext())) {
            new checkROMSupportList().execute(
                    getString(R.string.supported_roms_url),
                    "supported_roms.xml");
        }*/

        platformSummary = new StringBuilder();
        platformSummary.append(getString(R.string.android) + " " + References.getProp("ro.build" +
                ".version.release"));
        platformSummary.append(" (" + Build.ID + ")\n");
        platformSummary.append(getString(R.string.device) + " " + Build.MODEL + " (" + Build
                .DEVICE + ")" + "\n");
        platformSummary.append(getString(R.string.settings_about_oms_rro_version) + " ");
        platformSummary.append(((References.checkOMS(getContext())) ?
                getString(R.string.settings_about_oms_version_7) :
                getString(R.string.settings_about_rro_version_2)) + "\n");
        systemPlatform.setSummary(platformSummary);
        systemPlatform.setIcon(References.grabAppIcon(getContext(), "com.android.systemui"));

        Preference systemStatus = getPreferenceManager().findPreference
                ("system_status");
        boolean full_oms = References.checkOMS(getContext()) &&
                References.checkSubstratumFeature(getContext());
        boolean interfacer = References.checkThemeInterfacer(getContext());
        boolean verified = prefs.getBoolean("complexion", true);
        boolean certified = verified;
        if (References.checkOMS(getContext())) {
            certified = verified && full_oms;
        }

        systemStatus.setSummary((interfacer
                ? getString(R.string.settings_system_status_rootless)
                : getString(R.string.settings_system_status_rooted))
                + " (" + (certified ? getString(R.string
                .settings_system_status_certified) :
                getString(R.string.settings_system_status_uncertified)) + ")"
        );
        systemStatus.setIcon(certified ?
                getContext().getDrawable(R.drawable.system_status_certified)
                : getContext().getDrawable(R.drawable.system_status_uncertified));

        final CheckBoxPreference forceEnglish = (CheckBoxPreference)
                getPreferenceManager().findPreference("force_english_locale");
        boolean force = prefs.getBoolean("force_english", false);
        if (force) {
            forceEnglish.setChecked(true);
        } else {
            forceEnglish.setChecked(false);
        }
        forceEnglish.setOnPreferenceChangeListener(
                (preference, newValue) -> {
                    boolean isChecked = (Boolean) newValue;
                    if (isChecked) {
                        forceEnglish.setChecked(true);
                        Toast toast = Toast.makeText(getContext(), getString(R.string
                                        .settings_force_english_toast_success),
                                Toast.LENGTH_SHORT);
                        toast.show();
                        prefs.edit().putBoolean("force_english", true).apply();
                        References.forceEnglishLocale(getActivity().getApplicationContext());
                        getActivity().recreate();
                    } else {
                        forceEnglish.setChecked(false);
                        Toast toast = Toast.makeText(getContext(), getString(R.string
                                        .settings_force_english_toast_reverted),
                                Toast.LENGTH_SHORT);
                        toast.show();
                        prefs.edit().putBoolean("force_english", false).apply();
                        References.forceSystemLocale(getActivity().getApplicationContext());
                        getActivity().recreate();
                    }
                    return false;
                });

        final Preference purgeCache = getPreferenceManager().findPreference("purge_cache");
        purgeCache.setOnPreferenceClickListener(
                preference -> {
                    new deleteCache().execute("");
                    return false;
                });
        if (!References.isCachingEnabled(getContext())) purgeCache.setVisible(false);

        final CheckBoxPreference show_outdated_themes = (CheckBoxPreference)
                getPreferenceManager().findPreference("show_outdated_themes");
        if (prefs.getBoolean("display_old_themes", true)) {
            show_outdated_themes.setChecked(true);
        } else {
            show_outdated_themes.setChecked(false);
        }
        show_outdated_themes.setOnPreferenceChangeListener(
                (preference, newValue) -> {
                    boolean isChecked = (Boolean) newValue;
                    if (isChecked) {
                        prefs.edit().putBoolean("display_old_themes", true).apply();
                        show_outdated_themes.setChecked(true);
                    } else {
                        prefs.edit().putBoolean("display_old_themes", false).apply();
                        show_outdated_themes.setChecked(false);
                    }
                    return false;
                });

        final CheckBoxPreference alternate_drawer_design = (CheckBoxPreference)
                getPreferenceManager().findPreference("alternate_drawer_design");
        if (prefs.getBoolean("alternate_drawer_design", false)) {
            alternate_drawer_design.setChecked(true);
        } else {
            alternate_drawer_design.setChecked(false);
        }
        alternate_drawer_design.setOnPreferenceChangeListener(
                (preference, newValue) -> {
                    boolean isChecked = (Boolean) newValue;
                    if (isChecked) {
                        prefs.edit().putBoolean("alternate_drawer_design", true).apply();
                        alternate_drawer_design.setChecked(true);
                        Toast toast = Toast.makeText(getContext(), getString(R.string
                                        .substratum_restart_toast),
                                Toast.LENGTH_SHORT);
                        toast.show();
                        getActivity().recreate();
                    } else {
                        prefs.edit().putBoolean("alternate_drawer_design", false).apply();
                        alternate_drawer_design.setChecked(false);
                        Toast toast = Toast.makeText(getContext(), getString(R.string
                                        .substratum_restart_toast),
                                Toast.LENGTH_SHORT);
                        toast.show();
                        getActivity().recreate();
                    }
                    return false;
                });

        final CheckBoxPreference nougat_style_cards = (CheckBoxPreference)
                getPreferenceManager().findPreference("nougat_style_cards");
        if (prefs.getBoolean("nougat_style_cards", false)) {
            nougat_style_cards.setChecked(true);
        } else {
            nougat_style_cards.setChecked(false);
        }
        nougat_style_cards.setOnPreferenceChangeListener(
                (preference, newValue) -> {
                    boolean isChecked = (Boolean) newValue;
                    if (isChecked) {
                        prefs.edit().putBoolean("nougat_style_cards", true).apply();
                        nougat_style_cards.setChecked(true);
                    } else {
                        prefs.edit().putBoolean("nougat_style_cards", false).apply();
                        nougat_style_cards.setChecked(false);
                    }
                    return false;
                });

        final CheckBoxPreference vibrate_on_compiled = (CheckBoxPreference)
                getPreferenceManager().findPreference("vibrate_on_compiled");
        if (prefs.getBoolean("vibrate_on_compiled", true)) {
            vibrate_on_compiled.setChecked(true);
        } else {
            vibrate_on_compiled.setChecked(false);
        }
        vibrate_on_compiled.setOnPreferenceChangeListener(
                (preference, newValue) -> {
                    boolean isChecked = (Boolean) newValue;
                    if (isChecked) {
                        prefs.edit().putBoolean("vibrate_on_compiled", true).apply();
                        vibrate_on_compiled.setChecked(true);
                        return true;
                    } else {
                        prefs.edit().putBoolean("vibrate_on_compiled", false).apply();
                        vibrate_on_compiled.setChecked(false);
                        return false;
                    }
                });

        final CheckBoxPreference show_template_version = (CheckBoxPreference)
                getPreferenceManager().findPreference("show_template_version");
        if (prefs.getBoolean("show_template_version", true)) {
            show_template_version.setChecked(true);
        } else {
            show_template_version.setChecked(false);
        }
        show_template_version.setOnPreferenceChangeListener(
                (preference, newValue) -> {
                    boolean isChecked = (Boolean) newValue;
                    if (isChecked) {
                        prefs.edit().putBoolean("show_template_version", true).apply();
                        show_template_version.setChecked(true);
                    } else {
                        prefs.edit().putBoolean("show_template_version", false).apply();
                        show_template_version.setChecked(false);
                    }
                    return false;
                });

        final CheckBoxPreference dynamic_actionbar = (CheckBoxPreference)
                getPreferenceManager().findPreference("dynamic_actionbar");
        if (prefs.getBoolean("dynamic_actionbar", true)) {
            dynamic_actionbar.setChecked(true);
        } else {
            dynamic_actionbar.setChecked(false);
        }
        dynamic_actionbar.setOnPreferenceChangeListener(
                (preference, newValue) -> {
                    boolean isChecked = (Boolean) newValue;
                    if (isChecked) {
                        prefs.edit().putBoolean("dynamic_actionbar", true).apply();
                        dynamic_actionbar.setChecked(true);
                    } else {
                        prefs.edit().putBoolean("dynamic_actionbar", false).apply();
                        dynamic_actionbar.setChecked(false);
                    }
                    return false;
                });

        final CheckBoxPreference dynamic_navbar = (CheckBoxPreference)
                getPreferenceManager().findPreference("dynamic_navbar");
        if (prefs.getBoolean("dynamic_navbar", true)) {
            dynamic_navbar.setChecked(true);
        } else {
            dynamic_navbar.setChecked(false);
        }
        dynamic_navbar.setOnPreferenceChangeListener(
                (preference, newValue) -> {
                    boolean isChecked = (Boolean) newValue;
                    if (isChecked) {
                        prefs.edit().putBoolean("dynamic_navbar", true).apply();
                        dynamic_navbar.setChecked(true);
                    } else {
                        prefs.edit().putBoolean("dynamic_navbar", false).apply();
                        dynamic_navbar.setChecked(false);
                    }
                    return false;
                });

        final CheckBoxPreference themeCaching = (CheckBoxPreference)
                getPreferenceManager().findPreference("theme_caching");
        boolean to_show = prefs.getBoolean("caching_enabled", false);
        themeCaching.setChecked(to_show);
        themeCaching.setVisible(to_show);
        themeCaching.setOnPreferenceChangeListener(((preference, newValue) -> {
            boolean isChecked = (Boolean) newValue;
            prefs.edit().putBoolean("caching_enabled", isChecked).apply();
            purgeCache.setVisible(isChecked);
            new deleteCache().execute();
            return true;
        }));

        // These should run if the app is running in debug mode
        final Preference aoptSwitcher = getPreferenceManager().findPreference
                ("aopt_switcher");
        final CheckBoxPreference forceIndependence = (CheckBoxPreference)
                getPreferenceManager().findPreference("force_independence");

        if (BuildConfig.DEBUG) {
            if (prefs.getString("compiler", "aapt").equals("aapt")) {
                aoptSwitcher.setSummary(R.string.settings_aapt);
            } else {
                aoptSwitcher.setSummary(R.string.settings_aopt);
            }
            aoptSwitcher.setOnPreferenceClickListener(
                    preference -> {
                        SheetDialog sheetDialog =
                                new SheetDialog(getContext());
                        View sheetView = View.inflate(getContext(),
                                R.layout.aopt_sheet_dialog, null);

                        LinearLayout aapt = (LinearLayout) sheetView.findViewById(R.id.aapt);
                        LinearLayout aopt = (LinearLayout) sheetView.findViewById(R.id.aopt);
                        aapt.setOnClickListener(v -> {
                            prefs.edit().remove("compiler").apply();
                            prefs.edit().putString("compiler", "aapt").apply();
                            prefs.edit().putBoolean("aopt_debug", false).apply();
                            aoptSwitcher.setSummary(R.string.settings_aapt);
                            new AOPTCheck().injectAOPT(getContext(), true);
                            sheetDialog.hide();
                        });
                        aopt.setOnClickListener(v -> {
                            prefs.edit().remove("compiler").apply();
                            prefs.edit().putString("compiler", "aopt").apply();
                            prefs.edit().putBoolean("aopt_debug", true).apply();
                            aoptSwitcher.setSummary(R.string.settings_aopt);
                            new AOPTCheck().injectAOPT(getContext(), true);
                            sheetDialog.hide();
                        });
                        sheetDialog.setContentView(sheetView);
                        sheetDialog.show();
                        return false;
                    });

            if (References.checkThemeInterfacer(getContext())) {
                forceIndependence.setChecked(prefs.getBoolean("force_independence", false));
                forceIndependence.setOnPreferenceChangeListener(
                        (preference, newValue) -> {
                            boolean isChecked = (Boolean) newValue;
                            if (isChecked) {
                                final AlertDialog.Builder builder = new AlertDialog.Builder
                                        (getContext());
                                builder.setTitle(R.string.break_compilation_dialog_title);
                                builder.setMessage(R.string.break_compilation_dialog_content);
                                builder.setNegativeButton(R.string.break_compilation_dialog_cancel,
                                        (dialog, id) -> dialog.dismiss());
                                builder.setPositiveButton(R.string
                                                .break_compilation_dialog_continue,
                                        (dialog, id) -> {
                                            prefs.edit()
                                                    .putBoolean("force_independence", true).apply();
                                            forceIndependence.setChecked(true);
                                            Intent i = getContext().getPackageManager()
                                                    .getLaunchIntentForPackage(getContext()
                                                            .getPackageName());
                                            i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                                            startActivity(i);
                                        });
                                builder.show();
                            } else {
                                prefs.edit().putBoolean("force_independence", false).apply();
                                forceIndependence.setChecked(false);
                            }
                            return false;
                        });
            } else if (References.checkOMS(getContext())) {
                forceIndependence.setChecked(!References.checkThemeInterfacer(getContext()));
                forceIndependence.setEnabled(References.checkThemeInterfacer(getContext()));
            }
        } else {
            if (!References.checkOMS(getContext())) forceIndependence.setVisible(false);
            aoptSwitcher.setVisible(false);
            themeCaching.setVisible(false);

            // Hidden caching mode option
            systemPlatform.setOnPreferenceClickListener(preference -> {
                tapCount++;
                if (tapCount == 1) {
                    new Handler().postDelayed(() -> tapCount = 0, 2000);
                } else if (tapCount == 8) {
                    themeCaching.setVisible(true);
                    tapCount = 0;
                    if (getView() != null) {
                        Snackbar.make(getView(),
                                R.string.settings_theme_caching_found_snackbar,
                                Snackbar.LENGTH_LONG)
                                .show();
                    }
                    systemPlatform.setOnPreferenceClickListener(null);
                }
                return false;
            });
        }

        // Finally, these functions will only work on OMS ROMs
        if (References.checkOMS(getContext())) {
            Preference aboutInterfacer = getPreferenceManager().findPreference
                    ("about_interfacer");
            aboutInterfacer.setIcon(getContext().getDrawable(R.mipmap.restore_launcher));
            aboutInterfacer.setOnPreferenceClickListener(
                    preference -> {
                        try {
                            String sourceURL = getString(R.string.interfacer_github);
                            Intent i = new Intent(Intent.ACTION_VIEW);
                            i.setData(Uri.parse(sourceURL));
                            startActivity(i);
                        } catch (ActivityNotFoundException activityNotFoundException) {
                            //
                        }
                        return false;
                    });
            try {
                PackageInfo pInfo = References.getThemeInterfacerPackage(getContext());
                assert pInfo != null;
                String versionName = pInfo.versionName;
                int versionCode = pInfo.versionCode;
                aboutInterfacer.setSummary(prefs.getBoolean("force_independence", false) ?
                        getString(R.string.settings_about_interfacer_disconnected) :
                        versionName + " (" + versionCode + ")");
            } catch (Exception e) {
                if (References.isPackageInstalled(getContext(), References.MASQUERADE_PACKAGE)) {
                    aboutInterfacer.setSummary(
                            getString(R.string.settings_about_interfacer_masquerade));
                }
            }

            final CheckBoxPreference hide_app_checkbox = (CheckBoxPreference)
                    getPreferenceManager().findPreference("hide_app_checkbox");
            hide_app_checkbox.setEnabled(false);
            if (prefs.getBoolean("show_app_icon", true)) {
                hide_app_checkbox.setChecked(true);
            } else {
                hide_app_checkbox.setChecked(false);
            }
            if (checkSettingsPackageSupport()) {
                hide_app_checkbox.setSummary(getString(R.string.hide_app_icon_supported));
                hide_app_checkbox.setEnabled(true);
            }
            hide_app_checkbox.setOnPreferenceChangeListener(
                    (preference, newValue) -> {
                        boolean isChecked = (Boolean) newValue;
                        if (isChecked) {
                            prefs.edit().putBoolean("show_app_icon", true).apply();
                            PackageManager p = getContext().getPackageManager();
                            ComponentName componentName = new ComponentName(getContext(),
                                    LauncherActivity
                                            .class);
                            p.setComponentEnabledSetting(componentName, PackageManager
                                    .COMPONENT_ENABLED_STATE_ENABLED, PackageManager
                                    .DONT_KILL_APP);
                            if (getView() != null) {
                                Snackbar.make(getView(),
                                        getString(R.string.hide_app_icon_toast_disabled),
                                        Snackbar.LENGTH_LONG)
                                        .show();
                            }
                            hide_app_checkbox.setChecked(true);
                        } else {
                            prefs.edit().putBoolean("show_app_icon", false).apply();
                            PackageManager p = getContext().getPackageManager();
                            ComponentName componentName = new ComponentName(getContext(),
                                    LauncherActivity.class);
                            p.setComponentEnabledSetting(componentName, PackageManager
                                    .COMPONENT_ENABLED_STATE_DISABLED, PackageManager
                                    .DONT_KILL_APP);
                            if (getView() != null) {
                                Snackbar.make(getView(),
                                        getString(R.string.hide_app_icon_toast_enabled),
                                        Snackbar.LENGTH_LONG)
                                        .show();
                            }
                            hide_app_checkbox.setChecked(false);
                        }
                        return false;
                    });
            final CheckBoxPreference systemUIRestart = (CheckBoxPreference)
                    getPreferenceManager().findPreference("restart_systemui");
            if (prefs.getBoolean("systemui_recreate", true)) {
                systemUIRestart.setChecked(true);
            } else {
                systemUIRestart.setChecked(false);
            }
            systemUIRestart.setOnPreferenceChangeListener(
                    (preference, newValue) -> {
                        boolean isChecked = (Boolean) newValue;
                        if (isChecked) {
                            prefs.edit().putBoolean("systemui_recreate", true).apply();
                            if (getView() != null) {
                                Snackbar.make(getView(),
                                        getString(R.string.restart_systemui_toast_enabled),
                                        Snackbar.LENGTH_LONG)
                                        .show();
                            }
                            systemUIRestart.setChecked(true);
                        } else {
                            prefs.edit().putBoolean("systemui_recreate", false).apply();
                            if (getView() != null) {
                                Snackbar.make(getView(),
                                        getString(R.string.restart_systemui_toast_disabled),
                                        Snackbar.LENGTH_LONG)
                                        .show();
                            }
                            systemUIRestart.setChecked(false);
                        }
                        return false;
                    });
            if (!References.getProp("ro.substratum.recreate").equals("true"))
                systemUIRestart.setVisible(false);

            final CheckBoxPreference manager_disabled_overlays = (CheckBoxPreference)
                    getPreferenceManager().findPreference("manager_disabled_overlays");
            if (prefs.getBoolean("manager_disabled_overlays", true)) {
                manager_disabled_overlays.setChecked(true);
            } else {
                manager_disabled_overlays.setChecked(false);
            }
            manager_disabled_overlays.setOnPreferenceChangeListener(
                    (preference, newValue) -> {
                        boolean isChecked = (Boolean) newValue;
                        if (isChecked) {
                            prefs.edit().putBoolean("manager_disabled_overlays", true).apply();
                            manager_disabled_overlays.setChecked(true);
                            return true;
                        } else {
                            prefs.edit().putBoolean("manager_disabled_overlays", false).apply();
                            manager_disabled_overlays.setChecked(false);
                            return false;
                        }
                    });
            final CheckBoxPreference debugTheme = (CheckBoxPreference)
                    getPreferenceManager().findPreference("theme_debug");
            debugTheme.setChecked(prefs.getBoolean("theme_debug", false));
            debugTheme.setOnPreferenceChangeListener(
                    (preference, newValue) -> {
                        boolean isChecked = (Boolean) newValue;
                        if (isChecked) {
                            final AlertDialog.Builder builder = new AlertDialog.Builder
                                    (getContext());
                            builder.setTitle(R.string.break_compilation_dialog_title);
                            builder.setMessage(R.string.break_compilation_dialog_content);
                            builder.setNegativeButton(R.string.break_compilation_dialog_cancel,
                                    (dialog, id) -> dialog.dismiss());
                            builder.setPositiveButton(R.string.break_compilation_dialog_continue,
                                    (dialog, id) -> {
                                        prefs.edit()
                                                .putBoolean("theme_debug", true).apply();
                                        debugTheme.setChecked(true);
                                    });
                            builder.show();
                        } else {
                            prefs.edit().putBoolean("theme_debug", false).apply();
                            debugTheme.setChecked(false);
                        }
                        return false;
                    });
        }
    }

    private class deleteCache extends AsyncTask<String, Integer, String> {
        @Override
        protected void onPreExecute() {
            mProgressDialog = new ProgressDialog(getContext());
            mProgressDialog.setMessage(getString(R.string.substratum_cache_clear_initial_toast));
            mProgressDialog.setIndeterminate(true);
            mProgressDialog.setCancelable(false);
            mProgressDialog.show();
            References.clearAllNotifications(getContext());
        }

        @Override
        protected void onPostExecute(String result) {
            mProgressDialog.cancel();
        }

        @Override
        protected String doInBackground(String... sUrl) {
            // Delete the directory
            try {
                File dir = new File(getContext().getCacheDir().getAbsolutePath() +
                        "/SubstratumBuilder/");
                deleteDir(dir);
            } catch (Exception e) {
                // Suppress warning
            }
            // Reset the flag for is_updating
            SharedPreferences prefsPrivate =
                    getContext().getSharedPreferences("substratum_state",
                            Context.MODE_PRIVATE);
            prefsPrivate.edit().remove("is_updating").apply();
            return null;
        }

        boolean deleteDir(File dir) {
            if (dir != null && dir.isDirectory()) {
                String[] children = dir.list();
                for (String aChildren : children) {
                    boolean success = deleteDir(new File(dir, aChildren));
                    if (!success) {
                        return false;
                    }
                }
                return dir.delete();
            } else
                return dir != null && dir.isFile() && dir.delete();
        }
    }

    private class checkROMSupportList extends AsyncTask<String, Integer, String> {

        @Override
        protected void onPostExecute(String result) {
            super.onPostExecute(result);

            if (result != null) {
                String supportedRom = String.format(getString(R.string.rom_status_supported),
                        result.replaceAll("[^a-zA-Z0-9]", ""));
                platformSummary.append(getString(R.string.rom_status)).append(" ").append
                        (supportedRom);
                systemPlatform.setSummary(platformSummary.toString());
            } else {
                if (!References.isNetworkAvailable(getContext())) {
                    platformSummary.append(getString(R.string.rom_status)).append(" ").append
                            (getString(R.string.rom_status_network));
                    systemPlatform.setSummary(platformSummary.toString());
                } else {
                    platformSummary.append(getString(R.string.rom_status)).append(" ").append
                            (getString(R.string.rom_status_unsupported));
                    systemPlatform.setSummary(platformSummary.toString());
                }
            }
        }

        @Override
        protected String doInBackground(String... sUrl) {
            String inputFileName = sUrl[1];

            InputStream input = null;
            OutputStream output = null;
            HttpURLConnection connection = null;

            if (References.isNetworkAvailable(getContext())) {
                try {
                    URL url = new URL(sUrl[0]);
                    connection = (HttpURLConnection) url.openConnection();
                    connection.connect();

                    // expect HTTP 200 OK, so we don't mistakenly save error report
                    // instead of the file
                    if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
                        return "Server returned HTTP " + connection.getResponseCode()
                                + " " + connection.getResponseMessage();
                    }

                    // this will be useful to display download percentage
                    // might be -1: server did not report the length
                    int fileLength = connection.getContentLength();

                    // download the file
                    input = connection.getInputStream();

                    output = new FileOutputStream(getContext().getCacheDir().getAbsolutePath() +
                            "/" + inputFileName);

                    byte data[] = new byte[4096];
                    long total = 0;
                    int count;
                    while ((count = input.read(data)) != -1) {
                        // allow canceling with back button
                        if (isCancelled()) {
                            input.close();
                            return null;
                        }
                        total += count;
                        // publishing the progress....
                        if (fileLength > 0) // only if total length is known
                            publishProgress((int) (total * 100 / fileLength));
                        output.write(data, 0, count);
                    }
                } catch (Exception e) {
                    return e.toString();
                } finally {
                    try {
                        if (output != null)
                            output.close();
                        if (input != null)
                            input.close();
                    } catch (IOException ignored) {
                    }

                    if (connection != null)
                        connection.disconnect();
                }
            } else {
                File check = new File(getContext().getCacheDir().getAbsolutePath() +
                        "/" + inputFileName);
                if (!check.exists()) {
                    return null;
                }
            }

            ArrayList<String> listOfRoms =
                    ReadSupportedROMsFile.main(getContext().getCacheDir() + "/" + inputFileName);

            try {
                Boolean supported = false;
                String supported_rom = "";

                // First check ro.product.flavor
                Process process = Runtime.getRuntime().exec("getprop ro.build.flavor");
                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(process.getInputStream()));
                String line;
                while ((line = reader.readLine()) != null) {
                    for (int i = 0; i < listOfRoms.size(); i++) {
                        if (line.toLowerCase().contains(listOfRoms.get(i))) {
                            Log.d(References.SUBSTRATUM_LOG, "Supported ROM (1): " +
                                    listOfRoms.get(i));
                            supported_rom = listOfRoms.get(i);
                            supported = true;
                            break;
                        }
                    }
                    if (supported) break;
                }

                // If it's still not showing up in ro.product.flavor, check ro.ROM.name
                if (!supported) {
                    for (int i = 0; i < listOfRoms.size(); i++) {
                        Process process2 = Runtime.getRuntime().exec(
                                "getprop ro." + listOfRoms.get(i) + ".name");
                        BufferedReader reader2 = new BufferedReader(
                                new InputStreamReader(process2.getInputStream()));
                        String line2;
                        while ((line2 = reader2.readLine()) != null) {
                            if (line2.length() > 0) {
                                Log.d(References.SUBSTRATUM_LOG, "Supported ROM (2): " +
                                        listOfRoms.get(i));
                                supported_rom = listOfRoms.get(i);
                                supported = true;
                            }
                            if (supported) break;
                        }
                    }
                }

                // If it's still not showing up in ro.ROM.name, check ro.ROM.device
                if (!supported) {
                    for (int i = 0; i < listOfRoms.size(); i++) {
                        Process process3 = Runtime.getRuntime().exec(
                                "getprop ro." + listOfRoms.get(i) + ".device");
                        BufferedReader reader3 = new BufferedReader(
                                new InputStreamReader(process3.getInputStream()));
                        String line3;
                        while ((line3 = reader3.readLine()) != null) {
                            if (line3.length() > 0) {
                                Log.d(References.SUBSTRATUM_LOG, "Supported ROM (3): " +
                                        listOfRoms.get(i) + " (" + line3 + ")");
                                supported_rom = listOfRoms.get(i);
                                supported = true;
                            }
                            if (supported) break;
                        }
                    }
                }

                // Last case scenario, check the whole build prop
                if (!supported) {
                    for (int i = 0; i < listOfRoms.size(); i++) {
                        Process process4 = Runtime.getRuntime().exec("getprop");
                        BufferedReader reader4 = new BufferedReader(
                                new InputStreamReader(process4.getInputStream()));
                        String line4;
                        while ((line4 = reader4.readLine()) != null) {
                            if (line4.toLowerCase().contains(listOfRoms.get(i))) {
                                Log.d(References.SUBSTRATUM_LOG, "Supported ROM (4): " +
                                        listOfRoms.get(i) + " (" + line4 + ")");
                                if (listOfRoms.get(i).contains(".")) {
                                    supported_rom = listOfRoms.get(i).split("\\.")[1];
                                    supported = true;
                                } else {
                                    supported_rom = listOfRoms.get(i);
                                    supported = true;
                                }
                            }
                            if (supported) break;
                        }
                    }
                }
                reader.close();
                return supported_rom;
            } catch (Exception e) {
                // Suppress warning
            }

            return null;
        }
    }
}