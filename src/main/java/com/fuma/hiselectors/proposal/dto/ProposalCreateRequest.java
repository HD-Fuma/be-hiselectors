package com.fuma.hiselectors.proposal.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** 제안 발송 요청. 제목과 본문을 생략하면 서버 기본 템플릿을 사용한다. */
public record ProposalCreateRequest(
        @NotNull Long creatorId,
        @Size(max = 200, message = "제안 제목은 200자를 넘을 수 없습니다.")
        @Pattern(regexp = "[^\\r\\n]*\\S[^\\r\\n]*",
                message = "제안 제목은 공백이 아니어야 하며 줄바꿈을 포함할 수 없습니다.")
        String subject,
        @Size(max = 10000, message = "제안 메시지는 10,000자를 넘을 수 없습니다.")
        @Pattern(regexp = "(?s).*\\S.*", message = "제안 메시지는 공백일 수 없습니다.")
        String body) {

    public ProposalCreateRequest(Long creatorId) {
        this(creatorId, null, null);
    }

    @JsonIgnore
    @AssertTrue(message = "제안 제목과 메시지는 함께 입력해야 합니다.")
    public boolean isContentPairValid() {
        return (subject == null) == (body == null);
    }
}
