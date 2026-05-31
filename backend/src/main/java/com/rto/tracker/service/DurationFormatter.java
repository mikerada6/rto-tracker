package com.rto.tracker.service;

public final class DurationFormatter {

    private DurationFormatter() {}

    public static String format(long totalSeconds) {
        if (totalSeconds <= 0) {
            return "0m";
        }
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;

        if (hours > 0 && minutes > 0) {
            return hours + "h " + minutes + "m";
        } else if (hours > 0) {
            return hours + "h";
        } else {
            return minutes + "m";
        }
    }
}
