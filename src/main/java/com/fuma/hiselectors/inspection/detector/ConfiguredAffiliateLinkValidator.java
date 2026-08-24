package com.fuma.hiselectors.inspection.detector;

import com.fuma.hiselectors.inspection.config.ContentInspectionProperties;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class ConfiguredAffiliateLinkValidator implements AffiliateLinkValidator {

    private final List<String> allowedHosts;
    private final String selectorCodeParameter;

    public ConfiguredAffiliateLinkValidator(ContentInspectionProperties properties) {
        this.allowedHosts = properties.affiliateAllowedHostsOrEmpty();
        this.selectorCodeParameter = properties.affiliateCodeParameterOrDefault();
    }

    @Override
    public boolean isValid(String url, String selectorsCode) {
        try {
            URI uri = URI.create(url);
            if (!("http".equalsIgnoreCase(uri.getScheme())
                    || "https".equalsIgnoreCase(uri.getScheme()))) {
                return false;
            }
            if (uri.getHost() == null || (!allowedHosts.isEmpty()
                    && allowedHosts.stream().noneMatch(host -> host.equalsIgnoreCase(uri.getHost())))) {
                return false;
            }
            return selectorsCode != null && selectorsCode.equals(queryValue(uri, selectorCodeParameter));
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private String queryValue(URI uri, String name) {
        if (uri.getRawQuery() == null) {
            return null;
        }
        return Arrays.stream(uri.getRawQuery().split("&"))
                .map(parameter -> parameter.split("=", 2))
                .filter(pair -> URLDecoder.decode(pair[0], StandardCharsets.UTF_8).equals(name))
                .map(pair -> pair.length == 2
                        ? URLDecoder.decode(pair[1], StandardCharsets.UTF_8) : "")
                .findFirst().orElse(null);
    }
}
