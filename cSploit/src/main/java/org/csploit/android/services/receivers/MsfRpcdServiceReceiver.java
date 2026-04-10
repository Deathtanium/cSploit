package org.csploit.android.services.receivers;

import android.app.Activity;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.widget.Toast;

import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import org.csploit.android.R;
import org.csploit.android.helpers.ToastHelper;
import org.csploit.android.core.ManagedReceiver;
import org.csploit.android.core.System;
import org.csploit.android.services.MsfRpcdService;

/**
 * Receive and manage intents from the MsfRpcd service
 */
public class MsfRpcdServiceReceiver extends ManagedReceiver {

  final static int MSF_NOTIFICATION = 1337;
  private final IntentFilter filter;

  public MsfRpcdServiceReceiver() {
    filter = new IntentFilter();

    filter.addAction(MsfRpcdService.STATUS_ACTION);
  }

  @Override
  public IntentFilter getFilter() {
    return filter;
  }

  @Override
  public void onReceive(final Context context, Intent intent) {
    if(!MsfRpcdService.STATUS_ACTION.equals(intent.getAction())) {
      return;
    }

    final MsfRpcdService.Status status = (MsfRpcdService.Status)
            intent.getSerializableExtra(MsfRpcdService.STATUS);

    if(context instanceof Activity) {
      ((Activity) context).runOnUiThread(new Runnable() {
        @Override
        public void run() {
          showToastForStatus(context, status);
        }
      });
    } else {
      showToastForStatus(context, status);
    }

    SharedPreferences myPrefs = System.getSettings();
    if (myPrefs.getBoolean("MSF_NOTIFICATIONS", true)) {
      updateNotificationForStatus(context, status);
    }

  }

  private void showToastForStatus(Context context, MsfRpcdService.Status status) {
    String summary = context.getString(status.getText());
    String detail = msfRpcEndpointSummary(context);
    String msg = shouldAppendRpcDetail(status) ? summary + "\n" + detail : summary;
    ToastHelper.show(context, msg,
        status.isError() ? Toast.LENGTH_LONG : Toast.LENGTH_SHORT);
  }

  private static boolean shouldAppendRpcDetail(MsfRpcdService.Status status) {
    return status == MsfRpcdService.Status.STARTING
        || status == MsfRpcdService.Status.CONNECTED
        || status == MsfRpcdService.Status.CONNECTION_FAILED
        || status == MsfRpcdService.Status.START_FAILED
        || status == MsfRpcdService.Status.DISCONNECTED
        || status == MsfRpcdService.Status.STOPPED
        || status == MsfRpcdService.Status.KILLED;
  }

  private static String msfRpcEndpointSummary(Context context) {
    SharedPreferences p = System.getSettings();
    String host = p.getString("MSF_RPC_HOST", "127.0.0.1");
    int port = System.MSF_RPC_PORT;
    boolean ssl = p.getBoolean("MSF_RPC_SSL", false);
    String transport = context.getString(ssl ? R.string.msf_rpc_transport_tls : R.string.msf_rpc_transport_plain);
    return context.getString(R.string.msf_rpc_status_detail, host, port, transport);
  }

  private void updateNotificationForStatus(Context context, MsfRpcdService.Status status) {
    String summary = context.getString(status.getText());
    String detail = msfRpcEndpointSummary(context);
    String bigText = summary + "\n" + detail;

    NotificationCompat.Builder mBuilder =
            new NotificationCompat.Builder(context, context.getString(R.string.csploitChannelId))
            .setSmallIcon(R.drawable.exploit_msf)
            .setContentTitle(context.getString(R.string.msf_status))
            .setContentText(summary)
            .setStyle(new NotificationCompat.BigTextStyle()
                .setBigContentTitle(summary)
                .bigText(bigText)
                .setSummaryText(detail))
            .setProgress(0, 0, status.inProgress())
            .setOngoing(status == MsfRpcdService.Status.CONNECTED
                || status == MsfRpcdService.Status.STARTING)
            .setOnlyAlertOnce(true)
            .setColor(ContextCompat.getColor(context, status.getColor()))
            .setColorized(false)
            .setChannelId(context.getString(R.string.csploitChannelId))
            .setPriority(status.isError()
                ? NotificationCompat.PRIORITY_DEFAULT
                : NotificationCompat.PRIORITY_LOW);

    NotificationManager mNotificationManager =
            (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
    mNotificationManager.notify(MSF_NOTIFICATION, mBuilder.build());
  }
}
