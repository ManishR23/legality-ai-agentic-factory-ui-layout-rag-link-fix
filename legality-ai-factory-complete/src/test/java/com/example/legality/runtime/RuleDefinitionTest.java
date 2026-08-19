package com.example.legality.runtime;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RuleDefinitionTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private RuleDefinition def(String json) throws Exception {
        JsonNode node = MAPPER.readTree(json);
        return new RuleDefinition(node);
    }

    @Test
    void getIntParsesQuotedNumber() throws Exception {
        RuleDefinition d = def("{\"parameters\":{\"maxDutyHours\":\"14\"}}");
        assertEquals(14, d.getInt("maxDutyHours", 0));
    }

    @Test
    void getDoubleParsesQuotedNumber() throws Exception {
        RuleDefinition d = def("{\"parameters\":{\"minRestHours\":\"10.5\"}}");
        assertEquals(10.5, d.getDouble("minRestHours", 0.0), 0.0001);
    }

    @Test
    void getLongParsesQuotedNumber() throws Exception {
        RuleDefinition d = def("{\"parameters\":{\"windowMinutes\":\"1440\"}}");
        assertEquals(1440L, d.getLong("windowMinutes", 0L));
    }

    @Test
    void getIntStillHandlesNativeNumber() throws Exception {
        RuleDefinition d = def("{\"parameters\":{\"maxDutyHours\":14}}");
        assertEquals(14, d.getInt("maxDutyHours", 0));
    }

    @Test
    void uncoercibleStringFallsBackRatherThanThrowing() throws Exception {
        RuleDefinition d = def("{\"parameters\":{\"garbage\":\"not-a-number\"}}");
        assertEquals(5.0, d.getDouble("garbage", 5.0), 0.0001);
    }

    @Test
    void missingParameterFallsBack() throws Exception {
        RuleDefinition d = def("{\"parameters\":{}}");
        assertEquals(99, d.getInt("absent", 99));
    }

    @Test
    void typedParamParsesInstant() throws Exception {
        RuleDefinition d = def("{\"parameters\":{\"releaseTime\":\"2026-03-14T08:00:00Z\"}}");
        assertEquals(Instant.parse("2026-03-14T08:00:00Z"), d.param("releaseTime", Instant.class));
    }

    @Test
    void typedParamParsesLocalDate() throws Exception {
        RuleDefinition d = def("{\"parameters\":{\"pairingStart\":\"2026-03-14\"}}");
        assertEquals(LocalDate.parse("2026-03-14"), d.param("pairingStart", LocalDate.class));
    }

    @Test
    void typedParamParsesDoubleFromQuotedOrNativeNumber() throws Exception {
        RuleDefinition quoted = def("{\"parameters\":{\"minRestHours\":\"10.5\"}}");
        assertEquals(10.5, quoted.param("minRestHours", Double.class), 0.0001);
        RuleDefinition native_ = def("{\"parameters\":{\"minRestHours\":10.5}}");
        assertEquals(10.5, native_.param("minRestHours", Double.class), 0.0001);
    }

    @Test
    void typedParamReturnsNullWhenAbsent() throws Exception {
        RuleDefinition d = def("{\"parameters\":{}}");
        assertNull(d.param("missing", Instant.class));
    }

    @Test
    void typedParamReturnsNullWhenUnparseableRatherThanThrowing() throws Exception {
        RuleDefinition d = def("{\"parameters\":{\"releaseTime\":\"not-a-timestamp\"}}");
        assertNull(d.param("releaseTime", Instant.class));
    }

    @Test
    void typedParamRejectsUnsupportedTypeToken() throws Exception {
        RuleDefinition d = def("{\"parameters\":{\"x\":\"1\"}}");
        assertThrows(IllegalArgumentException.class, () -> d.param("x", Object.class));
    }
}
