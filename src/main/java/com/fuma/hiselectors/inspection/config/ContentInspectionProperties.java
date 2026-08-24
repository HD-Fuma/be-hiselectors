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
            "광고", "유료광고", "수수료를 받을 수 있습니다", "일정액의 수수료", "경제적 이해관계");

    public List<String> disclosurePhrasesOrDefault() {
        return disclosurePhrases == null || disclosurePhrases.isEmpty()
                ? DEFAULT_DISCLOSURE_PHRASES : List.copyOf(disclosurePhrases);
    }

    public List<String> affiliateAllowedHostsOrEmpty() {
        return affiliateAllowedHosts == null ? List.of() : List.copyOf(affiliateAllowedHosts);
    }

    public String affiliateCodeParameterOrDefault() {
        return affiliateCodeParameter == null || affiliateCodeParameter.isBlank()
                ? "ptrsRefCd" : affiliateCodeParameter;
    }
}
