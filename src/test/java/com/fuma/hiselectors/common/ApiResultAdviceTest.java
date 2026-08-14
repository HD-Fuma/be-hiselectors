package com.fuma.hiselectors.common;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ApiResultAdviceTest {

    private final ApiResultAdvice advice = new ApiResultAdvice();

    private Object wrap(Object body) {
        return advice.beforeBodyWrite(body, null, null, null, null, null);
    }

    @Test
    void DTO는_봉투로_감싼다() {
        Object wrapped = wrap("payload");

        assertThat(wrapped).isInstanceOf(ApiResult.class);
        ApiResult<?> result = (ApiResult<?>) wrapped;
        assertThat(result.success()).isTrue();
        assertThat(result.code()).isEqualTo("OK");
        assertThat(result.data()).isEqualTo("payload");
    }

    @Test
    void 이미_봉투면_이중_래핑하지_않는다() {
        ApiResult<Void> error = ApiResult.error("SELECTOR_NOT_FOUND", "없음");

        assertThat(wrap(error)).isSameAs(error);
    }

    @Test
    void null_본문은_감싸지_않는다() {
        assertThat(wrap(null)).isNull();
    }
}
