package com.fuma.hiselectors.inspection.detector;

import com.fuma.hiselectors.inspection.config.ContentInspectionProperties;
import java.net.URI;
import java.util.List;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class ConfiguredAffiliateLinkValidator implements AffiliateLinkValidator {

    private static final Pattern ALLOWED_PATH_PATTERN = Pattern.compile(
            "^/(?:shop/[^/]+|product/[^/]+|sellectors/manage/shop/[^/]+)/?$");

    private final List<String> allowedHosts;

    public ConfiguredAffiliateLinkValidator(ContentInspectionProperties properties) {
        this.allowedHosts = properties.affiliateAllowedHostsOrDefault();
    }

    @Override
    public boolean isValid(String url) {
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
            return uri.getPath() != null && ALLOWED_PATH_PATTERN.matcher(uri.getPath()).matches();
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
