/*
 * Runs cSploit tool handlers inside NetHunter chroot without cSploitd, dispatching {@link org.csploit.android.events.Event}s.
 */
package org.csploit.android.core;

import android.content.Context;
import android.content.SharedPreferences;

import org.csploit.android.events.Attempts;
import org.csploit.android.events.ChildEnd;
import org.csploit.android.events.Event;
import org.csploit.android.events.Hop;
import org.csploit.android.events.Host;
import org.csploit.android.events.Login;
import org.csploit.android.events.Message;
import org.csploit.android.events.Newline;
import org.csploit.android.events.Os;
import org.csploit.android.events.Port;
import org.csploit.android.events.Ready;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Process-based tool runner: {@code su} + NetHunter {@code bootkali_init} + {@code chroot}.
 */
public final class NethunterChildRunner {

  private static final AtomicInteger NEXT_ID = new AtomicInteger(0x70000001);
  private static final ConcurrentHashMap<Integer, Process> PROCESSES = new ConcurrentHashMap<>();

  private static volatile Context sAppContext;
  private static volatile String sBootkali;
  private static volatile String sChroot;

  private NethunterChildRunner() {
  }

  public static void init(Context ctx) {
    sAppContext = ctx.getApplicationContext();
    SharedPreferences p = NetHunterRuntime.defaultPrefs(sAppContext);
    sChroot = NetHunterRuntime.getChrootDir(p);
    sBootkali = NetHunterRuntime.resolveBootkaliInit(sAppContext);
  }

  public static int allocateChildId() {
    return NEXT_ID.getAndIncrement();
  }

  public static boolean isNethunterChildId(int childId) {
    return childId >= 0x70000000;
  }

  public static boolean tryKill(int childId, int signal) {
    Process p = PROCESSES.remove(childId);
    if (p == null) return false;
    if (p.isAlive()) {
      p.destroy();
    }
    return true;
  }

  /**
   * Starts the root/chroot process on a worker thread. The {@link Child} must already be registered in {@link ChildManager}.
   */
  public static void spawn(final int childId, final String handler, final String cmd, final String[] env,
                           final Child.EventReceiver receiver) {
    String inner = buildInnerInvocation(handler, cmd);
    if (inner == null) {
      Logger.error("NethunterChildRunner: unsupported handler " + handler);
      ChildManager.onEvent(childId, new ChildEnd(127));
      return;
    }
    String oneShot = NetHunterRuntime.chrootOneShot(sBootkali, sChroot, inner);
    NetHunterRuntime.prepareBootkaliIfNeeded(sBootkali, oneShot);
    final String suCmd = oneShot;
    final String h = handler;
    final String ccmd = cmd;
    org.csploit.android.helpers.ThreadHelper.getSharedExecutor().execute(new Runnable() {
      @Override
      public void run() {
        Process proc = null;
        try {
          proc = NetHunterRuntime.execSuOneShotMerged(suCmd);
          if (proc == null) {
            ChildManager.onEvent(childId, new ChildEnd(126));
            return;
          }
          PROCESSES.put(childId, proc);
          readLoop(childId, proc, h, ccmd);
        } catch (IOException e) {
          Logger.error("NethunterChildRunner: " + e.getMessage());
          ChildManager.onEvent(childId, new ChildEnd(126));
        } finally {
          PROCESSES.remove(childId, proc);
          if (proc != null && proc.isAlive()) {
            proc.destroy();
          }
        }
      }
    });
  }

  private static String buildInnerInvocation(String handler, String cmd) {
    if (cmd == null) cmd = "";
    switch (handler) {
      case "nmap":
        return "exec nmap " + cmd;
      case "raw":
        return "exec sh " + cmd;
      case "tcpdump":
        return "exec tcpdump " + cmd;
      case "hydra":
        return "exec hydra " + cmd;
      case "arpspoof":
        return "exec arpspoof " + cmd;
      case "ettercap":
        return "exec ettercap " + cmd;
      case "msfrpcd":
        return "exec msfrpcd " + cmd;
      case "network-radar":
        return "exec bash -lc 'while true; do arp-scan -I " + shellQuote(cmd.trim()) + " --localnet 2>/dev/null || true; sleep 4; done'";
      case "fusemounts":
        return "exec fusemounts " + cmd;
      case "blind":
        return "exec sh -c " + shellQuote(cmd);
      default:
        return null;
    }
  }

  private static String shellQuote(String s) {
    if (s == null) return "''";
    return "'" + s.replace("'", "'\\''") + "'";
  }

  private static final Pattern GREP_PORTS = Pattern.compile("Ports:\\s+(.+)$");
  private static final Pattern GREP_PORT_ENTRY = Pattern.compile("(\\d+)/([^/]+)/([^/]+)//([^/]*)//([^/]*)");
  private static final Pattern HOP_LINE = Pattern.compile("^\\s*(\\d+)\\s+([0-9.]+)\\s");
  private static final Pattern HYDRA_LOGIN = Pattern.compile("\\[(\\d+)\\]\\[([^]]+)]\\s+host:\\s+([^\\s]+)\\s+login:\\s+(\\S+)\\s+password:\\s+(\\S+)");
  private static final Pattern ARP_LINE = Pattern.compile("^([0-9.]+)\\s+([0-9a-fA-F:]{17})\\s*(.*)$");

  private static void readLoop(int childId, Process proc, String handler, String cmd) {
    BufferedReader in = new BufferedReader(new InputStreamReader(proc.getInputStream(), StandardCharsets.UTF_8));
    boolean xmlMode = "nmap".equals(handler) && cmd != null && cmd.contains("-oX -");
    StringBuilder xmlBuf = xmlMode ? new StringBuilder() : null;
    boolean readySent = false;
    try {
      String line;
      while ((line = in.readLine()) != null) {
        if (xmlBuf != null) {
          xmlBuf.append(line).append('\n');
          if (line.contains("</nmaprun>")) {
            dispatchNmapXml(childId, xmlBuf.toString());
            xmlBuf = null;
          }
          continue;
        }
        if (!dispatchLine(childId, handler, cmd, line)) {
          ChildManager.onEvent(childId, new Newline(line));
        }
        if ("msfrpcd".equals(handler) && !readySent) {
          if (line.contains("MSF") || line.contains("RPC") || line.contains("Bind") || line.contains("listening")) {
            readySent = true;
            ChildManager.onEvent(childId, new Ready());
          }
        }
      }
      if (xmlBuf != null && xmlBuf.length() > 0) {
        dispatchNmapXml(childId, xmlBuf.toString());
      }
      if ("msfrpcd".equals(handler) && !readySent) {
        ChildManager.onEvent(childId, new Ready());
      }
    } catch (IOException e) {
      Logger.debug("nh readLoop: " + e.getMessage());
    }

    int exit;
    try {
      exit = proc.waitFor();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      exit = -1;
    }
    ChildManager.onEvent(childId, new ChildEnd(exit));
  }

  /**
   * @return true if consumed (no extra Newline)
   */
  private static boolean dispatchLine(int childId, String handler, String cmd, String line) {
    if ("nmap".equals(handler)) {
      return dispatchNmapLine(childId, cmd, line);
    }
    if ("hydra".equals(handler)) {
      return dispatchHydraLine(childId, line);
    }
    if ("arpspoof".equals(handler) || "ettercap".equals(handler)) {
      try {
        ChildManager.onEvent(childId, new Message("ERROR", line));
      } catch (IllegalArgumentException e) {
        ChildManager.onEvent(childId, new Newline(line));
      }
      return true;
    }
    if ("network-radar".equals(handler)) {
      return dispatchArpScanLine(childId, line);
    }
    return false;
  }

  private static boolean dispatchNmapLine(int childId, String cmd, String line) {
    if (cmd != null && cmd.contains("--traceroute")) {
      Matcher hop = HOP_LINE.matcher(line);
      if (hop.find()) {
        try {
          int hopNum = Integer.parseInt(hop.group(1));
          InetAddress node = InetAddress.getByName(hop.group(2));
          ChildManager.onEvent(childId, new Hop(hopNum, 0L, node, ""));
          return true;
        } catch (Exception ignored) {
        }
      }
    }
    if (line.startsWith("Host: ") && line.contains("Ports:")) {
      Matcher gm = GREP_PORTS.matcher(line);
      if (gm.find()) {
        String plist = gm.group(1);
        for (String part : plist.split(",")) {
          part = part.trim();
          Matcher pe = GREP_PORT_ENTRY.matcher(part);
          if (pe.find()) {
            int port = Integer.parseInt(pe.group(1));
            String proto = pe.group(3);
            String service = pe.group(4).isEmpty() ? null : pe.group(4);
            String version = pe.group(5).isEmpty() ? null : pe.group(5);
            ChildManager.onEvent(childId, new Port(proto, port, service, version));
          }
        }
        return true;
      }
    }
    return false;
  }

  private static void dispatchNmapXml(int childId, String xml) {
    try {
      XmlPullParserFactory f = XmlPullParserFactory.newInstance();
      XmlPullParser p = f.newPullParser();
      p.setInput(new StringReader(xml));
      int ev;
      while ((ev = p.next()) != XmlPullParser.END_DOCUMENT) {
        if (ev == XmlPullParser.START_TAG) {
          String name = p.getName();
          if ("port".equals(name)) {
            String proto = p.getAttributeValue(null, "protocol");
            String portid = p.getAttributeValue(null, "portid");
            if (proto != null && portid != null) {
              int pn = Integer.parseInt(portid);
              ChildManager.onEvent(childId, new Port(proto, pn));
            }
          } else if ("osmatch".equals(name)) {
            String oname = p.getAttributeValue(null, "name");
            if (oname != null) {
              ChildManager.onEvent(childId, new Os((short) 100, oname, "generic"));
            }
          }
        }
      }
    } catch (Exception e) {
      Logger.debug("nmap xml: " + e.getMessage());
    }
  }

  private static boolean dispatchHydraLine(int childId, String line) {
    Matcher m = HYDRA_LOGIN.matcher(line);
    if (m.find()) {
      try {
        int port = Integer.parseInt(m.group(1));
        InetAddress addr = InetAddress.getByName(m.group(3));
        ChildManager.onEvent(childId, new Login(port, addr, m.group(4), m.group(5)));
        return true;
      } catch (Exception e) {
        Logger.debug("hydra parse: " + e.getMessage());
      }
    }
    if (line.contains("[ATTEMPT]") || line.contains("targets")) {
      ChildManager.onEvent(childId, new Attempts(1, 1, 0, 0));
      return true;
    }
    return false;
  }

  private static boolean dispatchArpScanLine(int childId, String line) {
    String t = line.trim();
    if (t.isEmpty() || t.startsWith("Interface:") || t.startsWith("Starting") || t.startsWith("Ending")) {
      return true;
    }
    Matcher m = ARP_LINE.matcher(t);
    if (m.find()) {
      try {
        InetAddress ip = InetAddress.getByName(m.group(1));
        byte[] mac = macToBytes(m.group(2));
        String name = m.group(3).trim();
        if (name.isEmpty()) name = null;
        ChildManager.onEvent(childId, new Host(mac, ip, name));
        return true;
      } catch (Exception e) {
        Logger.debug("arpscan parse: " + e.getMessage());
      }
    }
    return false;
  }

  private static byte[] macToBytes(String mac) {
    String[] p = mac.split(":");
    byte[] b = new byte[6];
    for (int i = 0; i < 6; i++) {
      b[i] = (byte) Integer.parseInt(p[i], 16);
    }
    return b;
  }
}
