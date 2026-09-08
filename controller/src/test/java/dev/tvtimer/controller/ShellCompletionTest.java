package dev.tvtimer.controller;
import org.junit.Test;
import static org.junit.Assert.*;
public class ShellCompletionTest {
    @Test public void ignoresEchoAndPartialMarker() {
        assertFalse(ShellCompletion.isComplete("tv:/ $ content call; echo DONE\r\n", "DONE"));
        assertFalse(ShellCompletion.isComplete("Result: Bundle[{ok=true}]\nDON", "DONE"));
    }
    @Test public void acceptsResultTerminator() {
        assertTrue(ShellCompletion.isComplete("Result: Bundle[{ok=true}]\r\nDONE\r\n", "DONE"));
    }
}
