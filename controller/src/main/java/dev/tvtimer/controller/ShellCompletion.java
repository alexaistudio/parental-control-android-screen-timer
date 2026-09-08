package dev.tvtimer.controller;

final class ShellCompletion {
    private ShellCompletion() { }
    static boolean isComplete(String output, String marker) {
        for (String line : output.split("\n", -1)) {
            if (line.replace("\r", "").equals(marker)) return true;
        }
        return false;
    }
}
