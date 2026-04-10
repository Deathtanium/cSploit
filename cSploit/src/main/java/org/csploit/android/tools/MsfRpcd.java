package org.csploit.android.tools;

import android.content.SharedPreferences;

import org.csploit.android.core.Child;
import org.csploit.android.core.ChildManager;
import org.csploit.android.core.NetHunterRuntime;
import org.csploit.android.core.System;
import org.csploit.android.events.Event;
import org.csploit.android.events.Ready;

/**
 * Metasploit RPC daemon, started inside the NetHunter Kali chroot via {@link org.csploit.android.core.NethunterChildRunner}.
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

  /**
   * start an MsfRpcd
   * @param receiver  will be notified when the daemon it's ready to accept connections
   */
  public Child async(String user, String pswd, int port, boolean ssl, MsfRpcdReceiver receiver) throws ChildManager.ChildNotStartedException {
    SharedPreferences p = System.getSettings();
    String bind = p.getString("MSF_RPC_BIND", "127.0.0.1");
    if (bind == null) {
      bind = "127.0.0.1";
    }
    bind = bind.trim();
    if (!bind.matches("^[0-9a-fA-F.:]+$")) {
      bind = "127.0.0.1";
    }
    String uEsc = NetHunterRuntime.escapeForSingleQuotedBash(user);
    String pEsc = NetHunterRuntime.escapeForSingleQuotedBash(pswd);
    String cmd = String.format("exec msfrpcd -P '%s' -U '%s' -p '%d' -a %s -n %s -t Msg -f",
        pEsc, uEsc, port, bind, (ssl ? "" : "-S"));
    return async(cmd, receiver);
  }

  /*public static boolean isLocal() {
    return System.getSettings().getString("MSF_RPC_HOST", "127.0.0.1").equals("127.0.0.1");
  }*/

  public static boolean isInstalled() {
    return System.getLocalMsfVersion() != null;
  }
}
