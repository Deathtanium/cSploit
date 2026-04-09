package org.csploit.android.services;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.view.MenuItem;

import org.csploit.android.R;
import org.csploit.android.core.Logger;
import org.csploit.android.core.System;
import org.csploit.android.net.metasploit.RPCClient;

/**
 * Metasploit RPC: connects to an existing msfrpcd (on-device or remote). Does not spawn the daemon.
 */
public class MsfRpcdService extends NativeService implements MenuControllableService {

  public static final String STATUS_ACTION = "MsfRpcdService.action.STATUS";
  public static final String STATUS = "MsfRpcdService.data.STATUS";

  final String host, user, password;
  final int port;
  final boolean ssl;

  @Override
  public void onMenuClick(Activity activity, final MenuItem item) {
    if (isConnected()) {
      stop();
    } else {
      start(true);
    }

    activity.runOnUiThread(new Runnable() {
      @Override
      public void run() {
        buildMenuItem(item);
      }
    });
  }

  @Override
  public void buildMenuItem(MenuItem item) {
    item.setTitle(
            isConnected() ? R.string.disconnect_msfrpcd : R.string.connect_msfrpcd);
    item.setEnabled(isAvailable());
  }

  public enum Status {
    STARTING(R.string.rpcd_starting, R.color.selectable_blue),
    CONNECTED(R.string.connected_msf, R.color.green),
    DISCONNECTED(R.string.msfrpc_disconnected, R.color.purple),
    STOPPED(R.string.rpcd_stopped, R.color.purple),
    KILLED(R.string.msfrpcd_killed, R.color.purple),
    START_FAILED(R.string.msfrcd_start_failed, R.color.red),
    CONNECTION_FAILED(R.string.msf_connection_failed, R.color.red);

    private final int text;
    private final int color;

    Status(int text, int color) {
      this.text = text;
      this.color = color;
    }

    public boolean inProgress() {
      return text == R.string.rpcd_starting;
    }

    public boolean isError() {
      return color == R.color.red;
    }

    public int getText() {
      return text;
    }

    public int getColor() {
      return color;
    }
  }

  public MsfRpcdService(Context context) {
    SharedPreferences prefs = System.getSettings();

    host = prefs.getString("MSF_RPC_HOST", "127.0.0.1");
    user = prefs.getString("MSF_RPC_USER", "msf");
    password = prefs.getString("MSF_RPC_PSWD", "msf");
    port = System.MSF_RPC_PORT;
    ssl = prefs.getBoolean("MSF_RPC_SSL", false);

    this.context = context;
  }

  private boolean isLocal() {
    return "127.0.0.1".equals(host);
  }

  public boolean isAvailable() {
    if (!System.isCoreInitialized()) {
      return false;
    }
    if (System.isServiceRunning("org.csploit.android.services.UpdateService")) {
      return false;
    }
    return true;
  }

  @Override
  protected int getStopSignal() {
    return 2;
  }

  /**
   * Try to open an RPC session to msfrpcd.
   *
   * @param notifyOnFailure if true, broadcast {@link Status#CONNECTION_FAILED} when unreachable (menu action).
   */
  public boolean start(boolean notifyOnFailure) {
    if (isConnected()) {
      return true;
    }

    stop();

    return connect(notifyOnFailure);
  }

  @Override
  public boolean start() {
    return start(true);
  }

  /**
   * @param notifyOnFailure broadcast connection failure when true
   * @return true if connection succeeded
   */
  private boolean connect(boolean notifyOnFailure) {
    try {
      System.setMsfRpc(new RPCClient(host, user, password, port, ssl));
      Logger.info("successfully connected to MSF RPC Daemon ");
      sendIntent(STATUS_ACTION, STATUS, Status.CONNECTED);
      return true;
    } catch (Exception e) {
      Logger.warning(e.getClass().getName() + ": " + e.getMessage());
    }

    if (notifyOnFailure) {
      sendIntent(STATUS_ACTION, STATUS, Status.CONNECTION_FAILED);
    }

    return false;
  }

  public boolean connect() {
    return connect(true);
  }

  public void disconnect() {
    System.setMsfRpc(null);
    sendIntent(STATUS_ACTION, STATUS, Status.DISCONNECTED);
  }

  private boolean isConnected() {
    return System.getMsfRpc() != null && System.getMsfRpc().isConnected();
  }

  @Override
  public boolean stop() {
    disconnect();
    return super.stop();
  }
}
