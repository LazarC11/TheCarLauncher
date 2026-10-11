package com.thecarlauncher.util;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.storage.StorageManager;
import android.os.storage.StorageVolume;
import android.provider.Settings;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * "All files access" (Android 11+) and the list of USB sticks / SD cards to scan. Many head units
 * never add USB sticks to Android's media index, so the launcher reads them as plain folders,
 * which needs this permission.
 */
public final class Storage {
    private Storage() {}

    public static boolean needsAllFiles() { return Build.VERSION.SDK_INT >= 30; }

    public static boolean hasAllFiles() {
        if (!needsAllFiles()) return true;
        try {
            return Environment.isExternalStorageManager();
        } catch (Throwable e) {
            return false;
        }
    }

    /** Opens the "All files access" page for this launcher. */
    public static void requestAllFiles(Context c) {
        if (!needsAllFiles()) return;
        Intent i = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                Uri.parse("package:" + c.getPackageName()));
        if (!Ui.start(c, i)) Ui.start(c, new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION));
    }

    /** Roots of removable storage (USB, SD) that can be read now. Internal storage is not included. */
    public static List<File> removableRoots(Context c) {
        Set<String> seen = new LinkedHashSet<>();
        if (Build.VERSION.SDK_INT >= 24) {
            try {
                StorageManager sm = (StorageManager) c.getSystemService(Context.STORAGE_SERVICE);
                for (StorageVolume v : sm.getStorageVolumes()) {
                    if (v.isPrimary() && !v.isRemovable()) continue;
                    String path = null;
                    if (Build.VERSION.SDK_INT >= 30) {
                        File d = v.getDirectory();
                        if (d != null) path = d.getPath();
                    } else {
                        try {
                            path = (String) StorageVolume.class.getMethod("getPath").invoke(v);
                        } catch (Throwable ignored) {
                        }
                    }
                    if (path != null) seen.add(path);
                }
            } catch (Throwable ignored) {
            }
        }
        // Fallbacks used by head-unit firmwares.
        File[] storage = new File("/storage").listFiles();
        if (storage != null) {
            for (File f : storage) {
                String n = f.getName();
                if (n.equals("emulated") || n.equals("self") || n.equals("sdcard0")) continue;
                seen.add(f.getPath());
            }
        }
        for (String p : new String[]{"/mnt/usb_storage", "/mnt/udisk", "/mnt/usbhost", "/mnt/usb", "/mnt/media_rw"}) {
            File f = new File(p);
            File[] kids = f.listFiles();
            if (kids != null) for (File k : kids) if (k.isDirectory()) seen.add(k.getPath());
        }
        List<File> out = new ArrayList<>();
        for (String p : seen) {
            File f = new File(p);
            if (f.isDirectory() && f.canRead()) out.add(f);
        }
        return out;
    }
}
