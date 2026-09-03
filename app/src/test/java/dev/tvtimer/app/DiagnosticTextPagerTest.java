package dev.tvtimer.app;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public final class DiagnosticTextPagerTest {
    @Test
    public void splitPreservesCompleteUnicodeTextWithinUtf8Limit() {
        String source = "start\n" + "Ошибка 📺\n".repeat(80) + "end";

        List<String> pages = DiagnosticTextPager.split(source, 120);

        assertTrue(pages.size() > 1);
        assertEquals(source, String.join("", pages));
        for (String page : pages) {
            assertTrue(page.getBytes(StandardCharsets.UTF_8).length <= 120);
        }
    }

    @Test
    public void emptyLogStillProducesOneQrPage() {
        assertEquals(1, DiagnosticTextPager.split("", 100).size());
    }
}
