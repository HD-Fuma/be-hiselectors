package com.fuma.hiselectors.proposal.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 제안 메일 템플릿 치환값. 발신 계정은 {@code spring.mail.*} 에서 온다.
 *
 * @param adminPosition 담당자 직급(예: 매니저)
 * @param adminEmail    본문·회신 안내에 노출할 담당자 이메일
 * @param applyUrl      메일에 노출할 셀렉터스 신청 페이지 링크
 */
@ConfigurationProperties(prefix = "proposal.mail")
public record ProposalMailProperties(
        String adminPosition,
        String adminEmail,
        String applyUrl) {
}
