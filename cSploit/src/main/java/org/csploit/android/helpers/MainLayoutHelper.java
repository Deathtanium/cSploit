package org.csploit.android.helpers;

import android.app.Activity;
import android.content.SharedPreferences;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

import org.csploit.android.R;

/**
 * Window insets for {@link R.layout#main} vs. standard action-bar activities (targetSdk 35).
 */
public final class MainLayoutHelper {

  private MainLayoutHelper() {
  }

  /**
   * After {@code setContentView}, pads the first child of {@code android.R.id.content} so it clears
   * status bar, display cutout, navigation bar, IME, and the app action bar. Required because
   * {@code setDecorFitsSystemWindows(true)} is not reliable for all Material/AppCompat combinations on API 35+.
   */
  public static void installInsetsForActivity(Activity activity) {
    WindowCompat.setDecorFitsSystemWindows(activity.getWindow(), false);
    final View content = activity.findViewById(android.R.id.content);
    if (!(content instanceof ViewGroup)) {
      return;
    }
    Runnable apply = () -> installInsetsOnFirstContentChild(activity, (ViewGroup) content);
    if (((ViewGroup) content).getChildCount() > 0) {
      apply.run();
    } else {
      content.post(apply);
    }
  }

  private static void installInsetsOnFirstContentChild(Activity activity, ViewGroup content) {
    if (content.getChildCount() == 0) {
      return;
    }
    View root = content.getChildAt(0);
    if (Boolean.TRUE.equals(root.getTag(R.id.tag_csploit_content_insets))) {
      return;
    }
    root.setTag(R.id.tag_csploit_content_insets, Boolean.TRUE);
    final int actionBarH = resolveActionBarHeight(activity);
    ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
      Insets bars = insets.getInsets(
          WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
      Insets ime = insets.getInsets(WindowInsetsCompat.Type.ime());
      int bottom = Math.max(bars.bottom, ime.bottom);
      v.setPadding(bars.left, bars.top + actionBarH, bars.right, bottom);
      return WindowInsetsCompat.CONSUMED;
    });
    ViewCompat.requestApplyInsets(root);
  }

  private static int resolveActionBarHeight(Activity activity) {
    TypedValue tv = new TypedValue();
    if (activity.getTheme().resolveAttribute(androidx.appcompat.R.attr.actionBarSize, tv, true)
        || activity.getTheme().resolveAttribute(android.R.attr.actionBarSize, tv, true)) {
      return TypedValue.complexToDimensionPixelSize(tv.data, activity.getResources().getDisplayMetrics());
    }
    return 0;
  }

  public static void installMainLayoutWithToolbar(AppCompatActivity activity) {
    WindowCompat.setDecorFitsSystemWindows(activity.getWindow(), false);
    activity.setContentView(R.layout.main);
    View mainRoot = activity.findViewById(R.id.main_root);
    ViewCompat.setOnApplyWindowInsetsListener(mainRoot, (v, insets) -> {
      Insets b = insets.getInsets(
          WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
      v.setPadding(b.left, b.top, b.right, b.bottom);
      return WindowInsetsCompat.CONSUMED;
    });
    ViewCompat.requestApplyInsets(mainRoot);
    Toolbar toolbar = activity.findViewById(R.id.toolbar);
    SharedPreferences themePrefs = activity.getSharedPreferences("THEME", 0);
    toolbar.setPopupTheme(themePrefs.getBoolean("isDark", false)
        ? R.style.ThemeOverlay_CSploit_Dark_PopupMenu
        : R.style.ThemeOverlay_AppCompat_Light);
    activity.setSupportActionBar(toolbar);
  }
}
