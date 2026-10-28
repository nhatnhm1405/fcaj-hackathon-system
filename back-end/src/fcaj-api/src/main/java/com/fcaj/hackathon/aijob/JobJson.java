package com.fcaj.hackathon.aijob;

import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;

final class JobJson {
    private JobJson() {}
    static final ObjectMapper MAPPER = new ObjectMapper(new com.fasterxml.jackson.core.JsonFactory()
            .setStreamReadConstraints(StreamReadConstraints.builder().maxNestingDepth(30).maxStringLength(2_000_000).build())
            .enable(com.fasterxml.jackson.core.JsonParser.Feature.STRICT_DUPLICATE_DETECTION))
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    static JsonNode parse(String value) {
        try { return MAPPER.readTree(value); }
        catch (Exception bad) { throw JobException.invalid("INVALID_JOB_JSON"); }
    }
    static byte[] bytes(JsonNode value) {
        try { return MAPPER.writeValueAsBytes(value); }
        catch (Exception bad) { throw new IllegalStateException("Cannot encode job JSON", bad); }
    }
    static ObjectNode object() { return MAPPER.createObjectNode(); }
    static String text(JsonNode node, String name, int max) {
        JsonNode value = node.get(name);
        if (value == null || !value.isTextual() || value.textValue().isBlank() || value.textValue().length() > max)
            throw JobException.invalid("INVALID_JOB_MESSAGE");
        return value.textValue();
    }
}
