package org.csploit.android.helpers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.IntentFilter;
import android.os.Build;

/**
 * API 33+ requires {@link Context#RECEIVER_EXPORTED} or {@link Context#RECEIVER_NOT_EXPORTED}
 * for dynamic {@link Context#registerReceiver(BroadcastReceiver, IntentFilter)}.
 */
public final class ContextReceiverCompat {

  private ContextReceiverCompat() {
  }

  /** In-app broadcasts only (default for cSploit internal actions and notification delete intents to this app). */
  public static void registerNotExported(Context context, BroadcastReceiver receiver, IntentFilter filter) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED);
    } else {
      context.registerReceiver(receiver, filter);
    }
  }
}
