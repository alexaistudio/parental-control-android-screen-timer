package dev.tvtimer.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Environment;
import android.util.Log;

import java.io.File;

public final class UsbRecoveryReceiver extends BroadcastReceiver {
    private static final String TAG = "TVTimerUsb";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (isRecoveryIntent(intent)) {
            performRecovery(context);
        }
    }

    static boolean isRecoveryIntent(Intent intent) {
        if (intent == null) {
            return false;
        }
        String action = intent.getAction();
        if (UsbRecoveryPolicy.shouldRecover(action, false)) {
            return true;
        }
        if (!Intent.ACTION_MEDIA_MOUNTED.equals(action)) {
            return false;
        }
        Uri data = intent.getData();
        if (data == null || data.getPath() == null) {
            return false;
        }
        try {
            boolean removable = Environment.isExternalStorageRemovable(new File(data.getPath()));
            return UsbRecoveryPolicy.shouldRecover(action, removable);
        } catch (IllegalArgumentException | SecurityException exception) {
            Log.w(TAG, "Unable to classify mounted storage; recovery was not triggered");
            return false;
        }
    }

    static void performRecovery(Context context) {
        ConfigStore store = new ConfigStore(context);
        if (!store.isConfigured()) {
            return;
        }
        if (store.resetForUsbRecovery()) {
            Log.i(TAG, "USB recovery cleared the local PIN and disabled enforcement");
        } else {
            Log.e(TAG, "USB recovery could not persist the reset");
        }
    }
}
