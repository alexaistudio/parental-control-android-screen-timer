package dev.tvtimer.controller;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;

import java.util.Locale;

final class ControllerLanguage {
    private static final String PREFS = "controller_settings";
    private static final String KEY = "language";

    private ControllerLanguage() {
    }

    static Context wrap(Context context) {
        String language = get(context);
        Locale locale = Locale.forLanguageTag(language);
        Locale.setDefault(locale);
        Configuration configuration = new Configuration(context.getResources().getConfiguration());
        configuration.setLocale(locale);
        return context.createConfigurationContext(configuration);
    }

    static String get(Context context) {
        SharedPreferences preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String stored = preferences.getString(KEY, null);
        if ("ru".equals(stored) || "en".equals(stored)) {
            return stored;
        }
        String system = Locale.getDefault().getLanguage();
        return "en".equalsIgnoreCase(system) ? "en" : "ru";
    }

    static void set(Context context, String language) {
        if (!"ru".equals(language) && !"en".equals(language)) {
            return;
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY, language)
                .apply();
    }
}
