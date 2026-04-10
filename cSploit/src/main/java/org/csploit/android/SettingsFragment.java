/*
 * This file is part of the dSploit.
 *
 * Copyleft of Simone Margaritelli aka evilsocket <evilsocket@gmail.com>
 *
 * dSploit is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * dSploit is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with dSploit.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.csploit.android;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import org.csploit.android.core.System;
import org.csploit.android.helpers.ToastHelper;
import org.csploit.android.gui.DirectoryPicker;
import org.csploit.android.services.Services;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.preference.EditTextPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceGroup;
import androidx.preference.TwoStatePreference;

public class SettingsFragment extends Fragment {

    public static final int SETTINGS_DONE = 1285;

    @SuppressWarnings("ConstantConditions")
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getActivity().getSupportFragmentManager().beginTransaction()
                .replace(android.R.id.content, new PrefsFrag())
                .commit();
    }

    public static class PrefsFrag extends PreferenceFragmentCompat implements SharedPreferences.OnSharedPreferenceChangeListener {

        private Preference mSavePath = null;
        private EditTextPreference mSnifferSampleTime = null;
        private EditTextPreference mProxyPort = null;
        private EditTextPreference mServerPort = null;
        private EditTextPreference mRedirectorPort = null;
        private EditTextPreference mMsfPort = null;
        private EditTextPreference mHttpBufferSize = null;
        private EditTextPreference mPasswordFilename = null;
        private TwoStatePreference mThemeChooser = null;
        private TwoStatePreference mMsfEnabled = null;

        @Override
        public void onViewCreated(View v, Bundle savedInstanceState) {
            super.onViewCreated(v, savedInstanceState);
            SharedPreferences themePrefs = getActivity().getSharedPreferences("THEME", 0);
            Boolean isDark = themePrefs.getBoolean("isDark", false);
            if (isDark) {
                v.setBackgroundColor(ContextCompat.getColor(getActivity(), R.color.background_window_dark));
            } else {
                v.setBackgroundColor(ContextCompat.getColor(getActivity(), R.color.background_window));
            }
        }

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);

            mSavePath = getPreferenceScreen().findPreference("PREF_SAVE_PATH");
            mMsfPort = (EditTextPreference) getPreferenceScreen().findPreference("MSF_RPC_PORT");
            mSnifferSampleTime = (EditTextPreference) getPreferenceScreen().findPreference("PREF_SNIFFER_SAMPLE_TIME");
            mProxyPort = (EditTextPreference) getPreferenceScreen().findPreference("PREF_HTTP_PROXY_PORT");
            mServerPort = (EditTextPreference) getPreferenceScreen().findPreference("PREF_HTTP_SERVER_PORT");
            mRedirectorPort = (EditTextPreference) getPreferenceScreen().findPreference("PREF_HTTPS_REDIRECTOR_PORT");
            mHttpBufferSize = (EditTextPreference) getPreferenceScreen().findPreference("PREF_HTTP_MAX_BUFFER_SIZE");
            mPasswordFilename = (EditTextPreference) getPreferenceScreen().findPreference("PREF_PASSWORD_FILENAME");
            mThemeChooser = (TwoStatePreference) getPreferenceScreen().findPreference("PREF_DARK_THEME");
            mMsfEnabled = (TwoStatePreference) getPreferenceScreen().findPreference("MSF_ENABLED");

            bindReadableDialogEditTexts(getPreferenceScreen());

            mThemeChooser.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object newValue) {
                    SharedPreferences themePrefs = getActivity().getBaseContext().getSharedPreferences("THEME", 0);
                    themePrefs.edit().putBoolean("isDark", (Boolean) newValue).apply();
                    ToastHelper.show(getActivity().getBaseContext(), getString(R.string.please_restart), Toast.LENGTH_LONG);
                    return true;
                }
            });

            mSavePath.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference preference) {
                    startDirectoryPicker(preference);
                    return true;
                }
            });
        }

        /**
         * EditTextPreference binds its field with the activity theme; in dark mode that yields
         * unreadable light text on the light alert surface. Force dialog-field colors on bind.
         */
        private void bindReadableDialogEditTexts(Preference pref) {
            if (pref instanceof EditTextPreference) {
                ((EditTextPreference) pref).setOnBindEditTextListener(editText -> {
                    editText.setTextColor(0xDE000000);
                    editText.setHintTextColor(0x99000000);
                    editText.setHighlightColor(Color.argb(64, 0x3F, 0x9F, 0xE0));
                    editText.setBackgroundResource(R.drawable.edit_text_dialog_border);
                });
            } else if (pref instanceof PreferenceGroup) {
                PreferenceGroup group = (PreferenceGroup) pref;
                for (int i = 0; i < group.getPreferenceCount(); i++) {
                    bindReadableDialogEditTexts(group.getPreference(i));
                }
            }
        }

        @Override
        public void onCreatePreferences(Bundle bundle, String s) {
            addPreferencesFromResource(R.xml.preferences);
        }

        @Override
        public void onActivityResult(int requestCode, int resultCode, Intent intent) {
            if (requestCode == DirectoryPicker.PICK_DIRECTORY && resultCode != AppCompatActivity.RESULT_CANCELED) {
                android.os.Bundle extras = intent.getExtras();
                if (extras == null) {
                    return;
                }
                String path = extras.getString(DirectoryPicker.CHOSEN_DIRECTORY);
                String key = extras.getString(DirectoryPicker.AFFECTED_PREF);
                if (path == null || key == null) {
                    return;
                }
                java.io.File folder = new java.io.File(path);
                if (!folder.exists()) {
                    ToastHelper.show(getActivity(), getString(R.string.pref_folder) + " " + path + " " + getString(R.string.pref_err_exists), Toast.LENGTH_SHORT);
                } else if (!folder.canWrite()) {
                    ToastHelper.show(getActivity(), getString(R.string.pref_folder) + " " + path + " " + getString(R.string.pref_err_writable), Toast.LENGTH_SHORT);
                } else {
                    getPreferenceManager().getSharedPreferences().edit().putString(key, path).apply();
                }
            }
        }

        private void startDirectoryPicker(Preference preference) {
            Intent i = new Intent(getActivity(), DirectoryPicker.class);
            i.putExtra(DirectoryPicker.AFFECTED_PREF, preference.getKey());
            startActivityForResult(i, DirectoryPicker.PICK_DIRECTORY);
        }

        @SuppressWarnings("ConstantConditions")
        @Override
        public void onResume() {
            super.onResume();
            getPreferenceScreen().getSharedPreferences().registerOnSharedPreferenceChangeListener(this);
        }

        @SuppressWarnings("ConstantConditions")
        @Override
        public void onPause() {
            super.onPause();
            getPreferenceScreen().getSharedPreferences().unregisterOnSharedPreferenceChangeListener(this);
        }

        public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
            String message = null;

            if (key.equals("PREF_SNIFFER_SAMPLE_TIME")) {
                double sampleTime;
                try {
                    sampleTime = Double.parseDouble(mSnifferSampleTime.getText());
                    if (sampleTime < 0.4 || sampleTime > 1.0) {
                        message = getString(R.string.pref_err_sample_time);
                        sampleTime = 1.0;
                    }
                } catch (Throwable t) {
                    message = getString(R.string.pref_err_invalid_number);
                    sampleTime = 1.0;
                }
                mSnifferSampleTime.setText(Double.toString(sampleTime));
            } else if (key.endsWith("_PORT")) {
                EditTextPreference prefPort = (EditTextPreference) getPreferenceScreen().findPreference(key);
                int port;
                try {
                    port = Integer.parseInt(prefPort != null ? prefPort.getText() : "0");
                    if (port < 1024 || port > 65535) {
                        message = getString(R.string.pref_err_port_range);
                        port = 0;
                    } else if (!System.isPortAvailable(port)) {
                        message = getString(R.string.pref_err_busy_port);
                        port = 0;
                    }
                } catch (Throwable t) {
                    message = getString(R.string.pref_err_invalid_number);
                    port = 0;
                }

                if (key.equals("PREF_HTTP_PROXY_PORT")) {
                    System.HTTP_PROXY_PORT = port;
                } else if (key.equals("PREF_HTTP_SERVER_PORT")) {
                    System.HTTP_SERVER_PORT = port;
                } else if (key.equals("PREF_HTTPS_REDIRECTOR_PORT")) {
                    System.HTTPS_REDIR_PORT = port;
                } else if (key.equals("MSF_RPC_PORT")) {
                    System.MSF_RPC_PORT = port;
                }

                if (port == 0) {
                    port = getDefaultPortForKey(key);
                    sharedPreferences.edit().putString(key, Integer.toString(port)).apply();
                }
            } else if (key.equals("PREF_HTTP_MAX_BUFFER_SIZE")) {
                int maxBufferSize;
                try {
                    maxBufferSize = Integer.parseInt(mHttpBufferSize.getText());
                    if (maxBufferSize < 1024 || maxBufferSize > 104857600) {
                        message = getString(R.string.pref_err_buffer_size);
                        maxBufferSize = 10485760;
                    }
                } catch (Throwable t) {
                    message = getString(R.string.pref_err_invalid_number);
                    maxBufferSize = 10485760;
                }
                mHttpBufferSize.setText(Integer.toString(maxBufferSize));
            } else if (key.equals("PREF_PASSWORD_FILENAME")) {
                String passFileName;
                try {
                    passFileName = mPasswordFilename.getText();
                    if (!passFileName.matches("[^/?*:;{}\\]+]")) {
                        message = getString(R.string.invalid_filename);
                        passFileName = "csploit-password-sniff.log";
                    }
                } catch (Throwable t) {
                    message = getString(R.string.invalid_filename);
                    passFileName = "csploit-password-sniff.log";
                }
                mPasswordFilename.setText(passFileName);
            } else if (key.equals("MSF_RPC_BIND")) {
                String b = sharedPreferences.getString("MSF_RPC_BIND", "127.0.0.1");
                if (b == null || !b.trim().matches("^[0-9a-fA-F.:]+$")) {
                    message = getString(R.string.pref_msf_rpc_bind_invalid);
                    sharedPreferences.edit().putString("MSF_RPC_BIND", "127.0.0.1").apply();
                }
            } else if (key.equals("PREF_AUTO_PORTSCAN")) {
                Services.getNetworkRadar().onAutoScanChanged();
            }

            if (message != null) {
                ToastHelper.show(getActivity(), message, Toast.LENGTH_SHORT);
            }

            System.onSettingChanged(key);
        }

        private int getDefaultPortForKey(String key) {
            switch (key) {
                case "PREF_HTTP_PROXY_PORT":
                    return 8080;
                case "PREF_HTTP_SERVER_PORT":
                    return 8081;
                case "PREF_HTTPS_REDIRECTOR_PORT":
                    return 8082;
                case "MSF_RPC_PORT":
                    return 55553;
                default:
                    return 0;
            }
        }

        @Override
        public boolean onOptionsItemSelected(MenuItem item) {
            if (item.getItemId() == android.R.id.home) {
                getActivity().onBackPressed();
                return true;
            }
            return super.onOptionsItemSelected(item);
        }
    }

    public void onBackPressed() {
        getActivity().finish();
        getActivity().overridePendingTransition(R.anim.fadeout, R.anim.fadein);
    }
}
