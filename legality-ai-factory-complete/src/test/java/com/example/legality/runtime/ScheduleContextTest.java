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
}
