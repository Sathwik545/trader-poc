package com.demo.aegis.persist;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/** Reads and writes the JSON columns (mandates, allowed FGTs, tag details, tags per rule). */
@Component
public class JsonColumns {

    private static final TypeReference<LinkedHashSet<String>> STRING_SET = new TypeReference<>() { };
    private static final TypeReference<LinkedHashMap<String, String>> STRING_MAP = new TypeReference<>() { };
    private static final TypeReference<LinkedHashMap<String, Integer>> COUNT_MAP = new TypeReference<>() { };

    private final ObjectMapper mapper;

    public JsonColumns(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    /** A JSON array of strings; null or empty text gives an empty set. */
    public Set<String> stringSet(String json) {
        return isBlank(json) ? Set.of() : read(json, STRING_SET);
    }

    public Map<String, String> stringMap(String json) {
        return isBlank(json) ? Map.of() : read(json, STRING_MAP);
    }

    public Map<String, Integer> countMap(String json) {
        return isBlank(json) ? Map.of() : read(json, COUNT_MAP);
    }

    public JsonNode tree(String json) {
        try {
            return isBlank(json) ? null : mapper.readTree(json);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Stored JSON cannot be read: " + e.getOriginalMessage(), e);
        }
    }

    public String write(Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Value cannot be written as JSON: " + e.getOriginalMessage(), e);
        }
    }

    private <T> T read(String json, TypeReference<T> type) {
        try {
            return mapper.readValue(json, type);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Stored JSON cannot be read: " + e.getOriginalMessage(), e);
        }
    }

    private static boolean isBlank(String json) {
        return json == null || json.isBlank();
    }
}
