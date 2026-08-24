package com.fuma.hiselectors.media.service;

import java.util.Optional;
import java.util.regex.Pattern;

final class CampaignThumbnailUrl {

    private static final Pattern MANAGED_KEY = Pattern.compile(
            "campaigns/[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-"
                    + "[0-9a-fA-F]{4}-[0-9a-fA-F]{12}\\.(jpg|png|webp)");

    private CampaignThumbnailUrl() {
    }

    static Optional<String> managedKey(String publicBaseUrl, String url) {
        if (publicBaseUrl == null || publicBaseUrl.isBlank() || url == null || url.isBlank()) {
            return Optional.empty();
        }
        String baseUrl = publicBaseUrl.replaceAll("/+$", "");
        String prefix = baseUrl + "/";
        if (!url.startsWith(prefix)) {
            return Optional.empty();
        }
        String key = url.substring(prefix.length());
        return MANAGED_KEY.matcher(key).matches() ? Optional.of(key) : Optional.empty();
    }
}
