package com.fuma.hiselectors.inspection.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "content-inspection")
public record ContentInspectionProperties(
        List<String> disclosurePhrases,
        List<String> affiliateAllowedHosts,
        String affiliateCodeParameter
) {
    private static final List<String> DEFAULT_DISCLOSURE_PHRASES = List.of(
            "광고",
            "유료광고",
            "수수료를 받을 수 있습니다",
            "일정액의 수수료",
            "경제적 이해관계",
            "본 콘텐츠는 더현대Hi 셀렉터스 활동의 일환으로 링크를 통해 구매가 발생할 경우 일정 수수료를 제공받습니다",
            "본 콘텐츠는 더현대Hi 셀렉터스 활동의 일환으로 셀렉터스샵을 통해 구매가 발생할 경우 일정 수수료를 제공받습니다");
    private static final List<String> DEFAULT_AFFILIATE_ALLOWED_HOSTS = List.of(
            "hi.thehyundai.com", "hiselectors.shop");

    public List<String> disclosurePhrasesOrDefault() {
        return disclosurePhrases == null || disclosurePhrases.isEmpty()
                ? DEFAULT_DISCLOSURE_PHRASES : List.copyOf(disclosurePhrases);
    }

    public List<String> affiliateAllowedHostsOrDefault() {
        return affiliateAllowedHosts == null || affiliateAllowedHosts.isEmpty()
                ? DEFAULT_AFFILIATE_ALLOWED_HOSTS : List.copyOf(affiliateAllowedHosts);
    }

    public String affiliateCodeParameterOrDefault() {
        return affiliateCodeParameter == null || affiliateCodeParameter.isBlank()
                ? "ptrsRefCd" : affiliateCodeParameter;
    }
}
