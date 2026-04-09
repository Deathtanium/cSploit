package org.csploit.android.core;

import android.content.Context;

import org.apache.commons.compress.utils.IOUtils;

import org.csploit.android.services.UpdateService;
import org.csploit.android.update.CoreUpdate;

/**
 * Installs the core payload shipped in {@code assets/core_bundled.xz} on first run so the app
 * does not need to download {@code core.tar.xz} from GitHub. Uses {@link UpdateService} extraction.
 */
public final class BundledCoreInstaller {

  private static volatile boolean sBundledInstallStarted = false;

  private BundledCoreInstaller() {
  }

  /** Called when a bundled core {@link UpdateService} run fails so a retry can be attempted. */
  public static void resetBundledInstallStarted() {
    sBundledInstallStarted = false;
  }

  public static boolean needsInstall() {
    return !System.isCoreInstalled();
  }

  public static final String BUNDLED_CORE_ASSET_NAME = "core_bundled.xz";

  public static boolean hasBundledCoreAsset(Context context) {
    java.io.InputStream probe = null;
    try {
      probe = context.getAssets().open(BUNDLED_CORE_ASSET_NAME);
      return true;
    } catch (java.io.IOException e) {
      return false;
    } finally {
      IOUtils.closeQuietly(probe);
    }
  }

  /**
   * If core is missing and {@link #BUNDLED_CORE_ASSET_NAME} exists in assets, starts {@link UpdateService}
   * with a {@link CoreUpdate} that copies from assets then extracts (no network).
   *
   * @return true if a bundled install was started or already started; false if not applicable
   */
  public static boolean startInstallIfNeeded(Context context) {
    if (!needsInstall()) {
      return false;
    }
    java.io.InputStream probe = null;
    try {
      probe = context.getAssets().open(BUNDLED_CORE_ASSET_NAME);
    } catch (java.io.IOException e) {
      Logger.debug("no bundled core asset: " + BUNDLED_CORE_ASSET_NAME);
      return false;
    } finally {
      IOUtils.closeQuietly(probe);
    }

    if (sBundledInstallStarted) {
      return true;
    }
    sBundledInstallStarted = true;

    CoreUpdate update = CoreUpdate.fromBundledAsset(context);
    android.content.Intent i = new android.content.Intent(context, UpdateService.class);
    i.setAction(UpdateService.START);
    i.putExtra(UpdateService.UPDATE, update);
    context.startService(i);
    return true;
  }
}
