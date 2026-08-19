package com.example.legality.runtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ScheduleContextTest {
    private final ScheduleContext ctx = new ScheduleContext(new ObjectMapper().createObjectNode());

    @Test
    void computesHoursBetweenValidTimestamps() {
        double hours = ctx.hoursBetween("2026-03-14T08:00:00", "2026-03-14T18:00:00");
        assertEquals(10.0, hours, 0.0001);
    }

    @Test
    void malformedTimestampThrowsRatherThanReturningZero() {
        assertThrows(IllegalArgumentException.class,
                () -> ctx.hoursBetween("2026-03-14 08:00", "2026-03-14T18:00:00"));
    }

    @Test
    void nullTimestampThrows() {
        assertThrows(IllegalArgumentException.class, () -> ctx.hoursBetween(null, "2026-03-14T18:00:00"));
    }

    @Test
    void computesHoursBetweenOffsetTimestamps() {
        // real timestamps the LLM-driven Test Scenario Agent has been observed to emit
        double hours = ctx.hoursBetween("2024-07-01T14:00:00-05:00", "2024-07-02T06:00:00-05:00");
        assertEquals(16.0, hours, 0.0001);
    }

    @Test
    void computesHoursBetweenZuluTimestamps() {
        double hours = ctx.hoursBetween("2026-03-14T08:00:00Z", "2026-03-14T18:00:00Z");
        assertEquals(10.0, hours, 0.0001);
    }

    @Test
    void computesHoursBetweenDifferentOffsetsCorrectly() {
        // 09:00-05:00 and 15:00+01:00 are the same instant in different offsets -> 0 hours apart
        double hours = ctx.hoursBetween("2026-03-14T09:00:00-05:00", "2026-03-14T15:00:00+01:00");
        assertEquals(0.0, hours, 0.0001);
    }
}
