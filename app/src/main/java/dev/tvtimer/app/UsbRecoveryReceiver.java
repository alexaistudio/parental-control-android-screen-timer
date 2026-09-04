package dev.tvtimer.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.util.Log;

import java.io.File;

public final class UsbRecoveryReceiver extends BroadcastReceiver {
    private static final String TAG = "ScreenTimerUsb";

    @Override
    public void onReceive(Context context, Intent intent) {
        ConfigStore store = new ConfigStore(context);
        if (!store.isUsbRecoveryEnabled()) {
            return;
        }
        File recoveryFile = findRecoveryFile(intent);
        if (recoveryFile == null) {
            return;
        }
        if (store.requestRecoveryMode()) {
            DiagnosticLog.info(
                    context,
                    TAG,
                    "Recovery file detected; parent mode requested"
            );
        }
    }

    static File findRecoveryFile(Intent intent) {
        if (intent == null) {
            return null;
        }
        String action = intent.getAction();
        if (!Intent.ACTION_MEDIA_MOUNTED.equals(action)) {
            return null;
        }
        Uri data = intent.getData();
        if (data == null || data.getPath() == null) {
            return null;
        }
        try {
            File[] files = new File(data.getPath()).listFiles();
            if (files == null) {
                return null;
            }
            for (File file : files) {
                if (file.isFile() && RecoveryFilePolicy.isRecoveryFileName(file.getName())) {
                    return UsbRecoveryPolicy.shouldOpenRecovery(action, true) ? file : null;
                }
            }
        } catch (SecurityException exception) {
            Log.w(TAG, "Unable to inspect mounted storage for Recovery file", exception);
        }
        return null;
    }
}
