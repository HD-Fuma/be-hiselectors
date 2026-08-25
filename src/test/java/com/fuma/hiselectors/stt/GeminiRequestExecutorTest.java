package com.fuma.hiselectors.stt;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClientException;

class GeminiRequestExecutorTest {

    @Test
    void 실패하면_다음_모델과_키로_전환한다() {
        GeminiProperties properties = new GeminiProperties(
                "key-1", "key-2", "fallback", "main", "youtube", "report", null, null);
        GeminiRequestExecutor executor = new GeminiRequestExecutor(properties);
        List<GeminiProperties.Attempt> tried = new ArrayList<>();

        String result = executor.execute("primary", attempt -> {
            tried.add(attempt);
            if (tried.size() < properties.attempts("primary").size()) {
                throw new RestClientException("quota");
            }
            return attempt.apiKey();
        });

        assertThat(result).isEqualTo("key-2");
        assertThat(tried).containsExactlyElementsOf(properties.attempts("primary"));
    }
}
