package dev.tvtimer.app;

import android.app.Application;

public final class ScreenTimerApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        DiagnosticLog.installCrashHandler(this);
    }
}
