package com.fuma.hiselectors.taskrun.service;

import com.fuma.hiselectors.taskrun.model.TaskType;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
public class RequestFingerprint {

    private final ObjectMapper objectMapper;

    public RequestFingerprint(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String of(TaskType taskType, JsonNode businessPayload) {
        Objects.requireNonNull(taskType, "taskType must not be null");
        Objects.requireNonNull(businessPayload, "businessPayload must not be null");
        byte[] canonical = (taskType.name() + "\n" + canonicalJson(businessPayload))
                .getBytes(StandardCharsets.UTF_8);
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(canonical));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }

    private String canonicalJson(JsonNode node) {
        if (node.isObject()) {
            List<String> names = new ArrayList<>();
            names.addAll(node.propertyNames());
            names.sort(String::compareTo);
            StringBuilder json = new StringBuilder("{");
            for (String name : names) {
                if (json.length() > 1) {
                    json.append(',');
                }
                json.append(quoted(name)).append(':').append(canonicalJson(node.get(name)));
            }
            return json.append('}').toString();
        }
        if (node.isArray()) {
            StringBuilder json = new StringBuilder("[");
            for (JsonNode element : node) {
                if (json.length() > 1) {
                    json.append(',');
                }
                json.append(canonicalJson(element));
            }
            return json.append(']').toString();
        }
        if (node.isNumber()) {
            BigDecimal normalized = node.decimalValue().stripTrailingZeros();
            return normalized.signum() == 0 ? "0" : normalized.toPlainString();
        }
        if (node.isString()) {
            return quoted(node.stringValue());
        }
        return node.toString();
    }

    private String quoted(String value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JacksonException e) {
            throw new IllegalArgumentException("JSON value cannot be canonicalized", e);
        }
    }
}
