package org.csploit.android.tools;

import android.content.SharedPreferences;

import org.csploit.android.core.*;
import org.csploit.android.core.System;
import org.csploit.android.events.Event;
import org.csploit.android.events.Ready;

/**
 * MetaSploit RPC Daemon
 */
public class MsfRpcd extends Msf {

  public static abstract class MsfRpcdReceiver extends Child.EventReceiver {
    @Override
    public void onEvent(Event e) {
      if(e instanceof Ready)
        onReady();
    }

    public abstract void onReady();
  }

  public MsfRpcd() {
    mHandler = "msfrpcd";
  }

  @Override
  public void setEnabled() {
    mEnabled = ChildManager.handlers != null && ChildManager.handlers.contains(mHandler);
    SharedPreferences prefs = System.getSettings();
    boolean rpcLocal = "127.0.0.1".equals(prefs.getString("MSF_RPC_HOST", "127.0.0.1"));
    if (rpcLocal && System.usesBundledMsfResources()) {
      mEnabled = mEnabled && (System.getLocalMsfVersion() != null ||
          ExecChecker.msf().canExecuteInDir(System.getMsfPath()));
    }
  }

  @Override
  protected void registerSettingReceiver() {
    super.registerSettingReceiver();
    onSettingsChanged.addFilter("MSF_RPC_HOST");
    onSettingsChanged.addFilter("MSF_USE_BUNDLED_RESOURCES");
  }

  /**
   * start an MsfRpcd
   * @param receiver  will be notified when the daemon it's ready to accept connections
   */
  public Child async(String user, String pswd, int port, boolean ssl, MsfRpcdReceiver receiver) throws ChildManager.ChildNotStartedException {
    return async(
            String.format("-P '%s' -U '%s' -p '%d' -a 127.0.0.1 -n %s -t Msg -f",
            pswd, user, port, (ssl ? "" : "-S")),
            receiver);
  }

  /*public static boolean isLocal() {
    return System.getSettings().getString("MSF_RPC_HOST", "127.0.0.1").equals("127.0.0.1");
  }*/

  public static boolean isInstalled() {
    if (!System.usesBundledMsfResources())
      return true;
    return System.getLocalMsfVersion() != null;
  }
}
