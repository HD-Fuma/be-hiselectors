package com.fuma.hiselectors.media.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

@Configuration
@Profile("!local")
public class S3MediaConfig {

    @Bean
    S3Client s3Client(S3MediaProperties properties) {
        return S3Client.builder()
                .region(Region.of(properties.region()))
                .build();
    }
}
