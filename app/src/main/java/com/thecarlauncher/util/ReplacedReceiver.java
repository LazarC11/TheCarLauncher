package com.thecarlauncher.util;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * After the setup app (MibDroid.apk) installed the build made for this unit over itself, the new
 * build opens straight into the setup assistant, at the step after the compatibility check. Only
 * then (a flag set by the assistant), not on every update. Android 10 and newer may not allow it
 * from the background; then "Open" in the installer or the Home key does the same.
 */
public class ReplacedReceiver extends BroadcastReceiver {
    public static final String P_OPEN_AFTER_UPDATE = "open_after_update";

    @Override
    public void onReceive(Context c, Intent i) {
        if (!Intent.ACTION_MY_PACKAGE_REPLACED.equals(i.getAction())) return;
        if (!Prefs.bool(c, P_OPEN_AFTER_UPDATE, false)) return;
        Prefs.put(c, P_OPEN_AFTER_UPDATE, false);
        try {
            c.startActivity(new Intent(c, com.thecarlauncher.LauncherActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP));
        } catch (Throwable ignored) {
        }
    }
}
