package com.example.legality.runtime;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
}
