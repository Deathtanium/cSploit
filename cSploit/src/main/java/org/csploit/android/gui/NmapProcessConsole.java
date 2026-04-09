/*
 * This file is part of the cSploit.
 *
 * cSploit is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package org.csploit.android.gui;

import android.app.Activity;
import android.view.View;
import android.widget.ScrollView;
import android.widget.TextView;

import org.csploit.android.R;
import org.csploit.android.helpers.ThreadHelper;

/**
 * Bottom panel that streams stdout/stderr from an nmap {@link org.csploit.android.core.Child}.
 */
public final class NmapProcessConsole {

  private static final int MAX_CHARS = 120000;

  private final Activity mActivity;
  private final View mContainer;
  private final ScrollView mScroll;
  private final TextView mOutput;
  private final StringBuilder mBuffer = new StringBuilder(4096);

  public NmapProcessConsole(Activity activity) {
    mActivity = activity;
    mContainer = activity.findViewById(R.id.nmapConsoleContainer);
    mScroll = activity.findViewById(R.id.nmapConsoleScroll);
    mOutput = activity.findViewById(R.id.nmapConsoleOutput);
    if (mOutput != null) {
      mOutput.setText("");
    }
  }

  public boolean isAvailable() {
    return mOutput != null && mScroll != null;
  }

  public void clear() {
    runOnUiThread(new Runnable() {
      @Override
      public void run() {
        mBuffer.setLength(0);
        if (mOutput != null) {
          mOutput.setText("");
        }
      }
    });
  }

  public void appendLine(final String line) {
    if (mOutput == null) {
      return;
    }
    runOnUiThread(new Runnable() {
      @Override
      public void run() {
        if (line != null) {
          mBuffer.append(line).append('\n');
          trimBuffer();
          mOutput.setText(mBuffer);
          mScroll.post(new Runnable() {
            @Override
            public void run() {
              mScroll.fullScroll(View.FOCUS_DOWN);
            }
          });
        }
      }
    });
  }

  public void setVisible(final boolean visible) {
    if (mContainer == null) {
      return;
    }
    runOnUiThread(new Runnable() {
      @Override
      public void run() {
        mContainer.setVisibility(visible ? View.VISIBLE : View.GONE);
      }
    });
  }

  private void trimBuffer() {
    int overflow = mBuffer.length() - MAX_CHARS;
    if (overflow > 0) {
      mBuffer.delete(0, overflow);
      mBuffer.insert(0, "…\n");
    }
  }

  private void runOnUiThread(Runnable r) {
    if (ThreadHelper.isOnMainThread()) {
      r.run();
    } else {
      mActivity.runOnUiThread(r);
    }
  }
}
