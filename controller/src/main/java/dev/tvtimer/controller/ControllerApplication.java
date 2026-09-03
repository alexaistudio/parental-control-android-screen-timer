package dev.tvtimer.controller;

import android.app.Application;

public final class ControllerApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        ControllerLog.install(this);
    }
}
