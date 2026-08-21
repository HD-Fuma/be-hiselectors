package com.fuma.hiselectors.inspection.model;

import com.fuma.hiselectors.inspection.config.ContentInspectionProperties;
import java.util.List;

public record InspectionRuleConfig(
        List<String> disclosurePhrases,
        List<String> affiliateAllowedHosts,
        String affiliateCodeParameter
) {
    public InspectionRuleConfig {
        disclosurePhrases = disclosurePhrases == null ? List.of() : List.copyOf(disclosurePhrases);
        affiliateAllowedHosts = affiliateAllowedHosts == null
                ? List.of() : List.copyOf(affiliateAllowedHosts);
    }

    public static InspectionRuleConfig from(ContentInspectionProperties properties) {
        return new InspectionRuleConfig(
                properties.disclosurePhrasesOrDefault(),
                properties.affiliateAllowedHostsOrEmpty(),
                properties.affiliateCodeParameterOrDefault());
    }
}
