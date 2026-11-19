package com.thecarlauncher.util;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;

import com.hoho.android.usbserial.driver.UsbSerialDriver;
import com.hoho.android.usbserial.driver.UsbSerialPort;
import com.hoho.android.usbserial.driver.UsbSerialProber;
import com.thecarlauncher.mib.Mib;

import java.util.List;

/**
 * Sends the ambient color to a WLED controller via USB Serial (No Root required).
 * Uses the usb-serial-for-android library for robust chip support.
 */
public final class WledUart {
    private static final String TAG = "WledUart";
    private static final String ACTION_USB_PERMISSION = "com.thecarlauncher.USB_PERMISSION";
    private static Handler sWorker;

    private WledUart() {}

    private static synchronized Handler worker() {
        if (sWorker == null) {
            HandlerThread t = new HandlerThread("wled-uart");
            t.start();
            sWorker = new Handler(t.getLooper());
        }
        return sWorker;
    }

    /** Synchronizes the current ambient color from preferences to WLED. */
    public static void boot(Context c) {
        send(c, Mib.BLUE);
    }

    /** Sends a color (ARGB) as a solid color command to the WLED controller. */
    public static void send(final Context context, final int color) {
        final Context app = context.getApplicationContext();

        // Berechne den JSON-Befehl vorab für die virtuelle Konsole (Logcat)
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;
        final String json = "{\"seg\":[{\"id\":0,\"col\":[[" + r + "," + g + "," + b + "]],\"fx\":0}]}\n";
        
        // Diese Zeile erscheint IMMER im Logcat, auch ohne Hardware!
        Log.i(TAG, "[VIRTUAL] Sending to WLED: " + json.trim());

        worker().post(new Runnable() {
            @Override
            public void run() {
                UsbManager manager = (UsbManager) app.getSystemService(Context.USB_SERVICE);
                List<UsbSerialDriver> availableDrivers = UsbSerialProber.getDefaultProber().findAllDrivers(manager);
                
                if (availableDrivers.isEmpty()) {
                    Log.d(TAG, "Hardware status: No USB device connected.");
                    return;
                }

                // Open the first available driver
                UsbSerialDriver driver = availableDrivers.get(0);
                UsbDevice device = driver.getDevice();

                if (!manager.hasPermission(device)) {
                    Log.d(TAG, "Hardware status: Requesting USB permission...");
                    int flags = PendingIntent.FLAG_UPDATE_CURRENT;
                    if (Build.VERSION.SDK_INT >= 31) flags |= 1 << 25; // FLAG_MUTABLE
                    
                    PendingIntent pi = PendingIntent.getBroadcast(app, 0, new Intent(ACTION_USB_PERMISSION), flags);
                    manager.requestPermission(device, pi);
                    return;
                }

                transmit(manager, driver, json);
            }
        });
    }

    private static void transmit(UsbManager manager, UsbSerialDriver driver, String json) {
        UsbDeviceConnection connection = manager.openDevice(driver.getDevice());
        if (connection == null) return;

        UsbSerialPort port = driver.getPorts().get(0);
        try {
            port.open(connection);
            port.setParameters(115200, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE);
            port.write(json.getBytes("UTF-8"), 1000);
            Log.i(TAG, "Hardware status: Successfully sent via USB.");
        } catch (Exception e) {
            Log.e(TAG, "Hardware status: Transmission error", e);
        } finally {
            try {
                port.close();
            } catch (Exception ignored) {}
        }
    }
}
