package dev.tvtimer.controller;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

public final class DiagnosticTextPagerTest {
    @Test
    public void preservesUnicodeAndHonorsUtf8PageLimit() {
        String source = "request\n" + "Ответ устройства 📺\n".repeat(80) + "done";

        List<String> pages = DiagnosticTextPager.split(source, 120);

        assertTrue(pages.size() > 1);
        assertEquals(source, String.join("", pages));
        for (String page : pages) {
            assertTrue(page.getBytes(StandardCharsets.UTF_8).length <= 120);
        }
    }

    @Test
    public void emptyLogStillHasOnePage() {
        assertEquals(1, DiagnosticTextPager.split("", 100).size());
    }
}
