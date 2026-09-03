package dev.tvtimer.app;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

final class DiagnosticTextPager {
    private DiagnosticTextPager() {
    }

    static List<String> split(String value, int maxUtf8Bytes) {
        if (maxUtf8Bytes <= 0) {
            throw new IllegalArgumentException("maxUtf8Bytes must be positive");
        }
        String source = value == null ? "" : value;
        if (source.isEmpty()) {
            return Collections.singletonList("");
        }
        List<String> pages = new ArrayList<>();
        StringBuilder page = new StringBuilder();
        int pageBytes = 0;
        for (int offset = 0; offset < source.length();) {
            int codePoint = source.codePointAt(offset);
            String symbol = new String(Character.toChars(codePoint));
            int symbolBytes = symbol.getBytes(StandardCharsets.UTF_8).length;
            if (page.length() > 0 && pageBytes + symbolBytes > maxUtf8Bytes) {
                pages.add(page.toString());
                page.setLength(0);
                pageBytes = 0;
            }
            page.append(symbol);
            pageBytes += symbolBytes;
            offset += Character.charCount(codePoint);
        }
        if (page.length() > 0) {
            pages.add(page.toString());
        }
        return pages;
    }
}
