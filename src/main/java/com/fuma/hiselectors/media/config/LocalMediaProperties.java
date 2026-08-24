package com.fuma.hiselectors.media.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "media.local")
public record LocalMediaProperties(String directory, String publicBaseUrl) {
}
