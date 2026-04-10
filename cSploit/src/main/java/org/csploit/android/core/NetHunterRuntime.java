/*
 * NetHunter Kali chroot integration (same conventions as Hijacker: bootkali_init + chroot bash).
 */
package org.csploit.android.core;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.preference.PreferenceManager;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Detects NetHunter chroot layout and builds {@code su} payloads that run tools inside Kali.
 */
public final class NetHunterRuntime {

  /** {@code auto} | {@code nethunter} | {@code legacy} */
  public static final String PREF_NETHUNTER_BACKEND = "PREF_NETHUNTER_BACKEND";

  private static final String DEFAULT_CHROOT = "/data/local/nhsystem/kali-arm64";
  private static final String NETHUNTER_PACKAGE = "com.offsec.nethunter";

  private static final String[] BOOTKALI_SCRIPT_DIRS = {
      "/data/data/com.offsec.nethunter/scripts/",
      "/data/data/com.offsec.nethunter/files/scripts/",
  };

  private NetHunterRuntime() {
  }

  public static boolean isNetHunterScriptsBootkaliPath(String path) {
    if (path == null) return false;
    return path.contains("/com.offsec.nethunter/scripts/")
        || path.contains("/com.offsec.nethunter/files/scripts/");
  }

  public static String nethunterAppDataDir(Context ctx) {
    try {
      ApplicationInfo ai = ctx.getPackageManager().getApplicationInfo(NETHUNTER_PACKAGE, 0);
      return ai.dataDir;
    } catch (PackageManager.NameNotFoundException e) {
      return null;
    }
  }

  /**
   * Resolves {@code bootkali_init} (or {@code bootkali_init_bypass}) to an absolute path if present.
   */
  public static String resolveBootkaliInit(Context ctx) {
    String pmDir = nethunterAppDataDir(ctx);
    if (pmDir != null) {
      for (String name : new String[]{"bootkali_init", "bootkali_init_bypass"}) {
        String p = pmDir + "/scripts/" + name;
        if (new File(p).exists()) return p;
        p = pmDir + "/files/scripts/" + name;
        if (new File(p).exists()) return p;
      }
    }
    for (String dir : BOOTKALI_SCRIPT_DIRS) {
      for (String name : new String[]{"bootkali_init", "bootkali_init_bypass"}) {
        String p = dir + name;
        if (new File(p).exists()) return p;
      }
    }
    return "bootkali_init";
  }

  public static String getChrootDir(SharedPreferences pref) {
    return pref.getString("PREF_CHROOT_DIR", DEFAULT_CHROOT);
  }

  /**
   * Backend selection: auto → nethunter when chroot + bash exist; else legacy VERSION file.
   */
  public static String backendMode(SharedPreferences pref) {
    String v = pref.getString(PREF_NETHUNTER_BACKEND, "auto");
    if (v == null || v.isEmpty()) return "auto";
    return v;
  }

  public static boolean probeChroot(Context ctx, SharedPreferences pref) {
    String chroot = getChrootDir(pref);
    File bash = new File(chroot + "/bin/bash");
    File sh = new File(chroot + "/bin/sh");
    return bash.exists() && bash.isFile() && sh.exists();
  }

  /**
   * Kali-style environment preamble (single-quoted bash -c body).
   */
  public static String getChrootEnvPreamble() {
    String[] env = {
        "USER=root",
        "SHELL=/bin/bash",
        "PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin",
        "TERM=dumb",
        "HOME=/root",
        "LOGNAME=root",
    };
    StringBuilder sb = new StringBuilder();
    for (String e : env) {
      sb.append("export ").append(e).append(" && ");
    }
    return sb.toString();
  }

  public static String escapeForSingleQuotedBash(String s) {
    if (s == null) return "";
    return s.replace("'", "'\\''");
  }

  /**
   * Inner payload: {@code chroot dir /bin/bash -c '…'} (caller wraps in su).
   */
  public static String chrootBashCommand(String chrootDir, String innerCommand) {
    String esc = escapeForSingleQuotedBash(innerCommand);
    return "chroot " + chrootDir + " /bin/bash -c '" + getChrootEnvPreamble() + esc + "'";
  }

  /**
   * One {@code su -c} line: {@code bootkali && chroot …} so bind mounts match NetHunter/Magisk.
   */
  public static String chrootOneShot(String bootkaliInit, String chrootDir, String innerCommand) {
    String ch = chrootBashCommand(chrootDir, innerCommand);
    if (!isNetHunterScriptsBootkaliPath(bootkaliInit)) {
      return ch;
    }
    return bootkaliInit + " && " + ch;
  }

  public static Process execSuOneShot(String shellCommand) throws java.io.IOException {
    try {
      ProcessBuilder pb = new ProcessBuilder("su", "-mm", "-c", shellCommand);
      return pb.start();
    } catch (java.io.IOException e) {
      ProcessBuilder pb = new ProcessBuilder("su", "-c", shellCommand);
      return pb.start();
    }
  }

  /** Merges stderr into stdout (matches cSploitd single-stream parsing for many tools). */
  public static Process execSuOneShotMerged(String shellCommand) throws java.io.IOException {
    try {
      ProcessBuilder pb = new ProcessBuilder("su", "-mm", "-c", shellCommand);
      pb.redirectErrorStream(true);
      return pb.start();
    } catch (java.io.IOException e) {
      ProcessBuilder pb = new ProcessBuilder("su", "-c", shellCommand);
      pb.redirectErrorStream(true);
      return pb.start();
    }
  }

  /**
   * Best-effort: read a root-only file into a string (e.g. chroot nmap data).
   */
  public static String slurpFileViaSu(String absolutePath) {
    Process p = null;
    BufferedReader br = null;
    try {
      p = execSuOneShot("cat '" + escapeForSingleQuotedBash(absolutePath) + "' 2>/dev/null");
      br = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8));
      StringBuilder sb = new StringBuilder();
      String line;
      while ((line = br.readLine()) != null) {
        sb.append(line).append('\n');
      }
      p.waitFor();
      return sb.toString();
    } catch (Exception e) {
      Logger.debug("slurpFileViaSu: " + e.getMessage());
      return null;
    } finally {
      try {
        if (br != null) br.close();
      } catch (java.io.IOException ignored) {
      }
      if (p != null) p.destroy();
    }
  }

  public static void prepareBootkaliIfNeeded(String bootkaliInit, String shellCommand) {
    if (shellCommand == null || !shellCommand.contains("chroot ")) return;
    if (!isNetHunterScriptsBootkaliPath(bootkaliInit)) return;
    if (shellCommand.startsWith(bootkaliInit + " && ")) return;
    Process p = null;
    try {
      p = execSuOneShot(bootkaliInit);
      if (p == null) return;
      long deadline = java.lang.System.currentTimeMillis() + 90000L;
      while (p.isAlive() && java.lang.System.currentTimeMillis() < deadline) {
        try {
          Thread.sleep(250);
        } catch (InterruptedException ie) {
          Thread.currentThread().interrupt();
          break;
        }
      }
    } catch (java.io.IOException e) {
      Logger.error("prepareBootkaliIfNeeded: " + e.getMessage());
    } finally {
      if (p != null) p.destroy();
    }
  }

  public static List<String> nethunterHandlerList() {
    List<String> h = new ArrayList<>();
    h.add("blind");
    h.add("nmap");
    h.add("raw");
    h.add("tcpdump");
    h.add("hydra");
    h.add("arpspoof");
    h.add("ettercap");
    h.add("msfrpcd");
    h.add("network-radar");
    h.add("fusemounts");
    return h;
  }

  public static SharedPreferences defaultPrefs(Context ctx) {
    return PreferenceManager.getDefaultSharedPreferences(ctx);
  }
}
