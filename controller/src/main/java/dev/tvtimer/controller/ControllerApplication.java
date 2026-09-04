package dev.tvtimer.controller;

import android.app.Application;

public final class ControllerApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        ControllerLog.install(this);
        ControllerLog.info("Process", "targetSdk=" + getApplicationInfo().targetSdkVersion
                + " android17LocalNetworkRuntimePermissionRequired="
                + (android.os.Build.VERSION.SDK_INT >= 37
                && getApplicationInfo().targetSdkVersion >= 37));
    }
}
