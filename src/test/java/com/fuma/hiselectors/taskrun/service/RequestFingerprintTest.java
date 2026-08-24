package com.fuma.hiselectors.taskrun.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.fuma.hiselectors.taskrun.model.TaskType;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class RequestFingerprintTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RequestFingerprint fingerprint = new RequestFingerprint(objectMapper);

    @Test
    void reorderedObjectKeysAndNumericRepresentationsHaveTheSameFingerprint() throws Exception {
        var first = objectMapper.readTree("""
                {"filters":{"limit":1.00,"active":true},"ids":[3,2,1]}
                """);
        var reordered = objectMapper.readTree("""
                {"ids":[3.0,2e0,1],"filters":{"active":true,"limit":1}}
                """);

        assertThat(fingerprint.of(TaskType.CONTENT_SYNC, first))
                .isEqualTo(fingerprint.of(TaskType.CONTENT_SYNC, reordered));
    }

    @Test
    void arrayOrderIsPreserved() throws Exception {
        var first = objectMapper.readTree("{" + "\"ids\":[1,2,3]}");
        var reordered = objectMapper.readTree("{" + "\"ids\":[3,2,1]}");

        assertThat(fingerprint.of(TaskType.CONTENT_SYNC, first))
                .isNotEqualTo(fingerprint.of(TaskType.CONTENT_SYNC, reordered));
    }

    @Test
    void changedValueOrTaskTypeHasADifferentFingerprint() throws Exception {
        var original = objectMapper.readTree("{" + "\"generationId\":1}");
        var changed = objectMapper.readTree("{" + "\"generationId\":2}");

        String originalFingerprint = fingerprint.of(TaskType.CONTENT_SYNC, original);

        assertThat(fingerprint.of(TaskType.CONTENT_SYNC, changed)).isNotEqualTo(originalFingerprint);
        assertThat(fingerprint.of(TaskType.CREATOR_SYNC, original)).isNotEqualTo(originalFingerprint);
        assertThat(originalFingerprint).matches("[0-9a-f]{64}");
    }
}
