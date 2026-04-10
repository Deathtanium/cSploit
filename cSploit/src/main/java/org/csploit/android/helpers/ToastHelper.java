package org.csploit.android.helpers;

import android.content.Context;
import android.text.TextUtils;
import android.view.ContextThemeWrapper;
import android.widget.Toast;

import androidx.annotation.StringRes;

import org.csploit.android.R;

/**
 * Toasts that stay readable regardless of the hosting activity theme (e.g. application dark theme
 * leaking into the default toast text colors on some OS versions).
 */
public final class ToastHelper {

  private ToastHelper() {
  }

  public static void show(Context context, CharSequence text, int duration) {
    if (context == null || TextUtils.isEmpty(text)) {
      return;
    }
    make(context, text, duration).show();
  }

  public static void show(Context context, @StringRes int resId, int duration) {
    if (context == null) {
      return;
    }
    make(context, context.getString(resId), duration).show();
  }

  /** Same as {@link Toast#makeText} but with a fixed light overlay theme for readable contrast. */
  public static Toast make(Context context, CharSequence text, int duration) {
    Context app = context.getApplicationContext();
    Context themed = new ContextThemeWrapper(app, R.style.ThemeOverlay_ToastReadable);
    return Toast.makeText(themed, text, duration);
  }
}
