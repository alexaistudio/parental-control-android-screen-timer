package dev.tvtimer.app;
import org.junit.Test;
import static org.junit.Assert.*;
public class MinuteInputTest {
    @Test public void acceptsExactMinutesAndBounds() {
        assertEquals(1L, LimitMath.parseDailyMinutes("1"));
        assertEquals(75L, LimitMath.parseDailyMinutes(" 75 "));
        assertEquals(1440L, LimitMath.parseDailyMinutes("1440"));
    }
    @Test public void rejectsInvalidInputWithoutClamping() {
        for (String value : new String[]{"", "0", "-1", "1441", "999999999999999999999", "1.5"}) {
            try { LimitMath.parseDailyMinutes(value); fail(value); }
            catch (NumberFormatException expected) { }
        }
    }
}
