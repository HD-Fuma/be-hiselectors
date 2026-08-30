package com.fuma.hiselectors.stt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;

class GeminiRequestExecutorTest {

    @Test
    void 실패하면_다음_모델과_키로_전환한다() {
        GeminiProperties properties = new GeminiProperties(
                "key-1", "key-2", "fallback", "main", null, null);
        GeminiRequestExecutor executor = new GeminiRequestExecutor(properties);
        List<GeminiProperties.Attempt> tried = new ArrayList<>();

        String result = executor.execute("primary", attempt -> {
            tried.add(attempt);
            if (tried.size() < properties.attempts("primary").size()) {
                throw new HttpClientErrorException(HttpStatus.TOO_MANY_REQUESTS);
            }
            return attempt.apiKey();
        });

        assertThat(result).isEqualTo("key-2");
        assertThat(tried).containsExactlyElementsOf(properties.attempts("primary"));
    }

    @Test
    void 인증_실패는_현재_키의_나머지_모델을_건너뛴다() {
        GeminiProperties properties = new GeminiProperties(
                "bad-key", "good-key", "fallback", "main", null, null);
        GeminiRequestExecutor executor = new GeminiRequestExecutor(properties);
        List<GeminiProperties.Attempt> tried = new ArrayList<>();

        String result = executor.execute("primary", attempt -> {
            tried.add(attempt);
            if (attempt.apiKey().equals("bad-key")) {
                throw new HttpClientErrorException(HttpStatus.UNAUTHORIZED);
            }
            return attempt.apiKey();
        });

        assertThat(result).isEqualTo("good-key");
        assertThat(tried).containsExactly(
                new GeminiProperties.Attempt("primary", "bad-key"),
                new GeminiProperties.Attempt("primary", "good-key"));
    }

    @Test
    void 잘못된_요청은_다른_후보로_재시도하지_않는다() {
        GeminiProperties properties = new GeminiProperties(
                "key-1", "key-2", "fallback", "main", null, null);
        GeminiRequestExecutor executor = new GeminiRequestExecutor(properties);
        List<GeminiProperties.Attempt> tried = new ArrayList<>();

        assertThatThrownBy(() -> executor.execute("primary", attempt -> {
            tried.add(attempt);
            throw new HttpClientErrorException(HttpStatus.BAD_REQUEST);
        })).isInstanceOf(HttpClientErrorException.class);

        assertThat(tried).containsExactly(new GeminiProperties.Attempt("primary", "key-1"));
    }

    @Test
    void 측정_실행은_실제_시도와_재시도_횟수를_반환한다() {
        GeminiProperties properties = new GeminiProperties(
                "key-1", null, "fallback", "main", null, null);
        GeminiRequestExecutor executor = new GeminiRequestExecutor(properties);

        GeminiRequestExecutor.Execution<String> execution =
                executor.executeMeasured("primary", attempt -> {
                    if (attempt.model().equals("primary")) {
                        throw new HttpClientErrorException(HttpStatus.TOO_MANY_REQUESTS);
                    }
                    return "success";
                });

        assertThat(execution.value()).isEqualTo("success");
        assertThat(execution.attemptCount()).isEqualTo(2);
        assertThat(execution.retryCount()).isEqualTo(1);
        assertThat(execution.selectedModel()).isEqualTo("fallback");
    }
}
