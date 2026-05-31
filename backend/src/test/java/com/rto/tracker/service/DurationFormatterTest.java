package com.rto.tracker.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class DurationFormatterTest {

    @Test
    void zeroSeconds() {
        assertThat(DurationFormatter.format(0)).isEqualTo("0m");
    }

    @Test
    void onlyMinutes() {
        assertThat(DurationFormatter.format(600)).isEqualTo("10m");
    }

    @Test
    void exactHour() {
        assertThat(DurationFormatter.format(3600)).isEqualTo("1h");
    }

    @Test
    void hoursAndMinutes() {
        assertThat(DurationFormatter.format(4500)).isEqualTo("1h 15m");
    }

    @Test
    void largerDuration() {
        // 7h 30m = 27000 seconds
        assertThat(DurationFormatter.format(27000)).isEqualTo("7h 30m");
    }

    @Test
    void negativeSeconds() {
        assertThat(DurationFormatter.format(-100)).isEqualTo("0m");
    }
}
