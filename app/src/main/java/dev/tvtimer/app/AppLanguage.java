package dev.tvtimer.app;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Build;
import android.os.LocaleList;

import java.util.Locale;

final class AppLanguage {
    static final String RUSSIAN = "ru";
    static final String ENGLISH = "en";
    static final String DEFAULT_LANGUAGE = RUSSIAN;

    private AppLanguage() {
    }

    static String normalize(String language) {
        return ENGLISH.equals(language) ? ENGLISH : RUSSIAN;
    }

    static Context wrap(Context base) {
        String language = new ConfigStore(base).getLanguage();
        Configuration configuration = localizedConfiguration(
                base.getResources().getConfiguration(),
                language
        );
        return base.createConfigurationContext(configuration);
    }

    static boolean select(Context context, String language) {
        String normalized = normalize(language);
        ConfigStore store = new ConfigStore(context);
        if (normalized.equals(store.getLanguage())) {
            apply(context, normalized);
            return true;
        }
        if (!store.setLanguage(normalized)) {
            return false;
        }
        apply(context, normalized);
        return true;
    }

    static void apply(Context context) {
        apply(context, new ConfigStore(context).getLanguage());
    }

    private static void apply(Context context, String language) {
        Locale locale = Locale.forLanguageTag(normalize(language));
        Locale.setDefault(locale);
        updateResources(context.getResources(), language);
        Context application = context.getApplicationContext();
        if (application != null && application != context) {
            updateResources(application.getResources(), language);
        }
    }

    @SuppressWarnings("deprecation")
    private static void updateResources(Resources resources, String language) {
        Configuration configuration = localizedConfiguration(
                resources.getConfiguration(),
                language
        );
        resources.updateConfiguration(configuration, resources.getDisplayMetrics());
    }

    // app/build.gradle disables language splits, so both bundled locales are always present.
    @SuppressLint("AppBundleLocaleChanges")
    @SuppressWarnings("deprecation")
    private static Configuration localizedConfiguration(
            Configuration source,
            String language
    ) {
        Locale locale = Locale.forLanguageTag(normalize(language));
        Configuration configuration = new Configuration(source);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            configuration.setLocales(new LocaleList(locale));
        } else {
            configuration.locale = locale;
        }
        configuration.setLayoutDirection(locale);
        return configuration;
    }
}
