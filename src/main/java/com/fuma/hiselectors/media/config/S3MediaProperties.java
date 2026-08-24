package com.fuma.hiselectors.media.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "media.s3")
public record S3MediaProperties(String region, String bucket, String publicBaseUrl) {
}
