package com.fuma.hiselectors.creator.discovery;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PublicEmailExtractorTest {

    private final PublicEmailExtractor extractor = new PublicEmailExtractor();

    @Test
    void 첫_유효_이메일을_추출한다() {
        assertThat(extractor.extract(
                "bad..mail@example.com 문의 First.Contact+brand@example.co.kr, second@example.com"))
                .contains("First.Contact+brand@example.co.kr");
    }

    @Test
    void 컬럼보다_긴_이메일은_건너뛴다() {
        String tooLong = "a".repeat(90) + "@example.com";

        assertThat(extractor.extract(tooLong + " safe@example.com"))
                .contains("safe@example.com");
    }
}
