package org.csploit.android.services;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.SystemClock;
import android.view.MenuItem;

import org.csploit.android.R;
import org.csploit.android.core.ChildManager;
import org.csploit.android.core.Logger;
import org.csploit.android.core.NetHunterRuntime;
import org.csploit.android.core.System;
import org.csploit.android.net.metasploit.RPCClient;
import org.csploit.android.tools.MsfRpcd;

import java.net.ConnectException;
import java.net.NoRouteToHostException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;

import javax.net.ssl.SSLException;

/**
 * The MSFRPC daemon manager
 */
public class MsfRpcdService extends NativeService implements MenuControllableService {

  public static final String STATUS_ACTION = "MsfRpcdService.action.STATUS";
  public static final String STATUS = "MsfRpcdService.data.STATUS";

  final String host, user, password;
  final int port;
  final boolean ssl;

  /** Cached probe so {@link #buildMenuItem} does not run su on every frame. */
  private long chrootProbeAtMs = 0;
  private boolean chrootProbeOpen = false;

  @Override
  public void onMenuClick(Activity activity, final MenuItem item) {
    if (isManagedLocalDaemon()) {
      if (isMsfrpcdUpForMenu()) {
        if (isRunning()) {
          stop();
        } else {
          NetHunterRuntime.stopMsfrpcdInChroot(activity.getApplicationContext(), port);
          disconnect();
          invalidateChrootProbe();
        }
      } else {
        start();
      }
    } else {
      if (isConnected()) {
        disconnect();
      } else {
        connect();
      }
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
            isManagedLocalDaemon() ?
                    (isMsfrpcdUpForMenu() ? R.string.stop_msfrpcd : R.string.start_msfrpcd) :
                    (isConnected() ? R.string.connect_msf : R.string.disconnect_msf));
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

  /**
   * Daemon is managed on this device (loopback or this host's WLAN IPv4).
   */
  private boolean isManagedLocalDaemon() {
    if (isLoopbackHost(host)) {
      return true;
    }
    try {
      String lan = System.getNetwork().getLocalAddressAsString();
      return lan != null && lan.equals(host);
    } catch (Exception e) {
      return false;
    }
  }

  private static boolean isLoopbackHost(String h) {
    if (h == null) {
      return false;
    }
    String t = h.trim().toLowerCase();
    return "127.0.0.1".equals(t) || "localhost".equals(t) || "::1".equals(t) || "0.0.0.0".equals(t);
  }

  public boolean isAvailable() {
    if (!isManagedLocalDaemon()) {
      return true;
    }
    if (System.getLocalMsfVersion() == null || !System.getTools().msfrpcd.isEnabled()) {
      return false;
    }
    if (System.isNethunterToolBridge()) {
      return true;
    }
    return !System.isServiceRunning("org.csploit.android.services.UpdateService");
  }

  @Override
  protected int getStopSignal() {
    return 2;
  }

  private void invalidateChrootProbe() {
    chrootProbeAtMs = 0;
  }

  /**
   * True if our child is running or (NetHunter) something is listening on the RPC port inside the chroot.
   */
  public boolean isMsfrpcdUpForMenu() {
    if (isRunning()) {
      return true;
    }
    if (!isManagedLocalDaemon() || !System.isNethunterToolBridge()) {
      return false;
    }
    long now = SystemClock.elapsedRealtime();
    if (now - chrootProbeAtMs > 2500) {
      chrootProbeAtMs = now;
      chrootProbeOpen = NetHunterRuntime.isTcpOpenOnChrootLoopback(context, port);
    }
    return chrootProbeOpen;
  }

  @Override
  public boolean start() {
    if (isConnected()) {
      return true;
    }

    disconnect();

    if (connect(false)) {
      if (isManagedLocalDaemon()) {
        Logger.warning("connected to an existing MSF RPC instance");
      }
      return true;
    }

    if (!isManagedLocalDaemon()) {
      return false;
    }

    if (isMsfrpcdUpForMenu() && !isRunning()) {
      Logger.warning("RPC port is open but login failed; not spawning a second msfrpcd");
      sendIntent(STATUS_ACTION, STATUS, Status.CONNECTION_FAILED);
      return false;
    }

    try {
      nativeProcess = System.getTools().msfrpcd.async(user, password, port, ssl, new Receiver());
      invalidateChrootProbe();
      return true;
    } catch (ChildManager.ChildNotStartedException e) {
      Logger.error(e.getMessage());
      sendIntent(STATUS_ACTION, STATUS, Status.START_FAILED);
    }
    return false;
  }

  /** Host order for RPCClient: prefs host first, then device WLAN IPv4 when probing loopback on NetHunter. */
  public static List<String> orderedRpcHosts(Context ctx) {
    return new MsfRpcdService(ctx).buildRpcConnectHosts();
  }

  /**
   * Short, user-facing hint for a failed RPC test (see also logcat for the full exception).
   */
  public static String diagnosisForRpcFailure(Context ctx, Throwable e) {
    if (e == null) {
      return ctx.getString(R.string.msf_test_hint_generic);
    }
    for (Throwable t = e; t != null; t = t.getCause()) {
      if (t instanceof RPCClient.MSFException) {
        String m = t.getMessage();
        return ctx.getString(R.string.msf_test_hint_auth, m != null ? m : t.getClass().getSimpleName());
      }
      if (t instanceof SSLException) {
        return ctx.getString(R.string.msf_test_hint_ssl);
      }
      if (t instanceof SocketTimeoutException) {
        return ctx.getString(R.string.msf_test_hint_timeout);
      }
      if (t instanceof UnknownHostException) {
        return ctx.getString(R.string.msf_test_hint_unknown_host);
      }
      if (t instanceof NoRouteToHostException) {
        return ctx.getString(R.string.msf_test_hint_route);
      }
      if (t instanceof ConnectException) {
        return ctx.getString(R.string.msf_test_hint_refused);
      }
      String msg = t.getMessage();
      if (msg != null) {
        String lm = msg.toLowerCase();
        if (lm.contains("connection refused") || lm.contains("econnrefused")) {
          return ctx.getString(R.string.msf_test_hint_refused);
        }
        if (lm.contains("timed out") || lm.contains("timeout")) {
          return ctx.getString(R.string.msf_test_hint_timeout);
        }
        if (lm.contains("network is unreachable")) {
          return ctx.getString(R.string.msf_test_hint_route);
        }
        if (lm.contains("connection reset")) {
          return ctx.getString(R.string.msf_test_hint_reset);
        }
      }
    }
    return ctx.getString(R.string.msf_test_hint_generic);
  }

  private List<String> buildRpcConnectHosts() {
    List<String> hosts = new ArrayList<>();
    if (host != null) {
      hosts.add(host.trim());
    }
    if (System.isNethunterToolBridge() && isLoopbackHost(host)) {
      try {
        String lan = System.getNetwork().getLocalAddressAsString();
        if (lan != null && !lan.isEmpty() && !hosts.contains(lan) && !isLoopbackHost(lan)) {
          hosts.add(lan);
        }
      } catch (Exception e) {
        Logger.debug("rpcConnectHosts: " + e.getMessage());
      }
    }
    return hosts;
  }

  /**
   * connect to this msfrpcd instance
   * @param silent quietly fail if true
   * @return true if connection succeeded, false otherwise
   */
  private boolean connect(boolean silent) {
    List<String> hosts = buildRpcConnectHosts();
    int maxRounds = isManagedLocalDaemon() ? 45 : 1;
    for (int round = 0; round < maxRounds; round++) {
      for (String h : hosts) {
        try {
          System.setMsfRpc(new RPCClient(h, user, password, port, ssl));
          if (System.getMsfRpc().isConnected()) {
            Logger.info("connected to MSF RPC at " + h + ":" + port);
            sendIntent(STATUS_ACTION, STATUS, Status.CONNECTED);
            invalidateChrootProbe();
            return true;
          }
        } catch (Exception e) {
          Logger.warning(e.getClass().getName() + ": " + e.getMessage());
        }
      }
      if (round + 1 < maxRounds) {
        try {
          Thread.sleep(700);
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
          break;
        }
      }
    }

    if (!silent) {
      sendIntent(STATUS_ACTION, STATUS, Status.CONNECTION_FAILED);
    }
    return false;
  }

  /**
   * connect to this msfrpcd instance
   * @return true if connection succeeded, false otherwise
   */
  public boolean connect() {
    return connect(false);
  }

  public void disconnect() {
    System.setMsfRpc(null);
    if (!isManagedLocalDaemon()) {
      sendIntent(STATUS_ACTION, STATUS, Status.DISCONNECTED);
    }
    invalidateChrootProbe();
  }

  private boolean isConnected() {
    return System.getMsfRpc() != null && System.getMsfRpc().isConnected();
  }

  @Override
  public boolean stop() {
    disconnect();
    boolean stoppedOur = super.stop();
    if (isManagedLocalDaemon() && System.isNethunterToolBridge() && !stoppedOur && isMsfrpcdUpForMenu()) {
      NetHunterRuntime.stopMsfrpcdInChroot(context, port);
    }
    invalidateChrootProbe();
    return stoppedOur;
  }

  private class Receiver extends MsfRpcd.MsfRpcdReceiver {
    @Override
    public void onReady() {
      connect();
    }

    @Override
    public void onStart(String cmd) {
      sendIntent(STATUS_ACTION, STATUS, Status.STARTING);
    }

    @Override
    public void onDeath(int signal) {
      if (!isConnected()) {
        disconnect();
      }
      invalidateChrootProbe();
      sendIntent(STATUS_ACTION, STATUS, Status.KILLED);
    }

    @Override
    public void onEnd(int exitValue) {
      if (!isConnected()) {
        disconnect();
      }
      invalidateChrootProbe();
      sendIntent(STATUS_ACTION, STATUS, Status.STOPPED);
    }
  }
}
