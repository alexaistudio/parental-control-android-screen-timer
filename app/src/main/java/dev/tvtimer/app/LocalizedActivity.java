package dev.tvtimer.app;

import android.app.Activity;
import android.content.Context;

abstract class LocalizedActivity extends Activity {
    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(AppLanguage.wrap(newBase));
    }
}
