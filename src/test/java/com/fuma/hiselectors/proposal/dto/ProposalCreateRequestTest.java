package com.fuma.hiselectors.proposal.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

class ProposalCreateRequestTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void 제목과_본문을_함께_검증한다() {
        assertThat(validator.validate(new ProposalCreateRequest(1L, "제안 제목", "제안 본문")))
                .isEmpty();
        assertThat(validator.validate(new ProposalCreateRequest(1L, "제안 제목", null)))
                .isNotEmpty();
        assertThat(validator.validate(new ProposalCreateRequest(1L, "제안\n제목", "제안 본문")))
                .isNotEmpty();
        assertThat(validator.validate(new ProposalCreateRequest(1L, "   ", "   ")))
                .hasSizeGreaterThanOrEqualTo(2);
        assertThat(validator.validate(new ProposalCreateRequest(
                1L, "제안 제목", "가".repeat(10001))))
                .isNotEmpty();
    }

    @Test
    void 기존_creatorId_요청도_기본_템플릿_요청으로_읽는다() throws Exception {
        ProposalCreateRequest request = new ObjectMapper().readValue(
                "{\"creatorId\":5}", ProposalCreateRequest.class);

        assertThat(request).isEqualTo(new ProposalCreateRequest(5L));
        assertThat(validator.validate(request)).isEmpty();
    }
}
