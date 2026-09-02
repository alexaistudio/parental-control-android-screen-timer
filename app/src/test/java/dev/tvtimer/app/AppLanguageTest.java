package dev.tvtimer.app;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public final class AppLanguageTest {
    @Test
    public void supportsRussianAndEnglishOnly() {
        assertEquals(AppLanguage.RUSSIAN, AppLanguage.normalize(AppLanguage.RUSSIAN));
        assertEquals(AppLanguage.ENGLISH, AppLanguage.normalize(AppLanguage.ENGLISH));
    }

    @Test
    public void unsupportedOrMissingLanguageFallsBackToRussian() {
        assertEquals(AppLanguage.RUSSIAN, AppLanguage.normalize(null));
        assertEquals(AppLanguage.RUSSIAN, AppLanguage.normalize("de"));
        assertEquals(AppLanguage.RUSSIAN, AppLanguage.normalize("EN"));
    }
}
