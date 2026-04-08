
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

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.text.HtmlCompat;
import androidx.preference.PreferenceManager;

import android.widget.Toast;

import java.util.ArrayList;

public class MainActivity extends AppCompatActivity {

  private static final String PREF_LAB_USE_ACK_V1 = "PREF_LAB_USE_ACK_V1";

  MainFragment f;
  final static int MY_PERMISSIONS_WANTED = 1;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    SharedPreferences themePrefs = getSharedPreferences("THEME", 0);
    if (themePrefs.getBoolean("isDark", false)) {
      setTheme(R.style.DarkTheme);
    } else {
      setTheme(R.style.AppTheme);
    }

    if (!PreferenceManager.getDefaultSharedPreferences(this).getBoolean(PREF_LAB_USE_ACK_V1, false)) {
      showLabAcknowledgmentDialog(savedInstanceState);
      return;
    }

    startMainExperience(savedInstanceState);
  }

  private void showLabAcknowledgmentDialog(final Bundle savedInstanceState) {
    new AlertDialog.Builder(this)
        .setTitle(R.string.lab_ack_title)
        .setMessage(HtmlCompat.fromHtml(getString(R.string.csploit_disclaimer), HtmlCompat.FROM_HTML_MODE_LEGACY))
        .setCancelable(false)
        .setPositiveButton(R.string.lab_ack_accept, (d, which) -> {
          PreferenceManager.getDefaultSharedPreferences(MainActivity.this)
              .edit()
              .putBoolean(PREF_LAB_USE_ACK_V1, true)
              .apply();
          startMainExperience(savedInstanceState);
        })
        .setNegativeButton(R.string.exit, (d, which) -> finish())
        .show();
  }

  private void startMainExperience(Bundle savedInstanceState) {
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
      NotificationChannel mChannel = new NotificationChannel(getString(R.string.csploitChannelId),
          getString(R.string.cSploitChannelDescription), NotificationManager.IMPORTANCE_DEFAULT);
      NotificationManager mNotificationManager =
          (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
      if (mNotificationManager != null) {
        mNotificationManager.createNotificationChannel(mChannel);
      }
    }
    setContentView(R.layout.main);
    if (findViewById(R.id.mainframe) != null) {
      if (savedInstanceState != null) {
        return;
      }
      f = new MainFragment();
      getSupportFragmentManager().beginTransaction()
          .add(R.id.mainframe, f).commit();
    }
    verifyPerms();
  }

  public void verifyPerms() {
        ArrayList<String> wanted = new ArrayList<>();
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.S_V2
                && ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
          wanted.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE)
                != PackageManager.PERMISSION_GRANTED) {
          wanted.add(Manifest.permission.READ_PHONE_STATE);
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WAKE_LOCK)
                != PackageManager.PERMISSION_GRANTED) {
          wanted.add(Manifest.permission.WAKE_LOCK);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
          wanted.add(Manifest.permission.POST_NOTIFICATIONS);
        }
        if (!wanted.isEmpty()) {
          ActivityCompat.requestPermissions(this, wanted.toArray(new String[0]), MY_PERMISSIONS_WANTED);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String permissions[], int[] grantResults) {
        switch (requestCode) {
            case MY_PERMISSIONS_WANTED: {
                boolean allGranted = grantResults.length > 0;
                for (int r : grantResults) {
                    if (r != PackageManager.PERMISSION_GRANTED) {
                        allGranted = false;
                        break;
                    }
                }
                if (allGranted) {
                    Toast.makeText(this, R.string.permissions_succeed, Toast.LENGTH_LONG).show();
                } else {
                    Toast.makeText(this, R.string.permissions_fail, Toast.LENGTH_LONG).show();
                    finish();
                }
                break;
            }
        }
    }
}