package com.fuma.hiselectors.media.service;

import com.fuma.hiselectors.exception.BusinessException;
import com.fuma.hiselectors.exception.ErrorCode;
import com.fuma.hiselectors.media.config.S3MediaProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

@Component
@Profile("!local")
@RequiredArgsConstructor
public class S3CampaignThumbnailStorage implements CampaignThumbnailStorage {

    private static final String CACHE_CONTROL = "public, max-age=31536000, immutable";

    private final S3Client s3Client;
    private final S3MediaProperties properties;

    @Override
    public String store(String key, String contentType, byte[] bytes) {
        validateConfiguration();
        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(properties.bucket())
                .key(key)
                .contentType(contentType)
                .cacheControl(CACHE_CONTROL)
                .build();
        try {
            s3Client.putObject(request, RequestBody.fromBytes(bytes));
        } catch (SdkException e) {
            throw new BusinessException(ErrorCode.MEDIA_UPLOAD_FAILED, e);
        }
        return properties.publicBaseUrl().replaceAll("/+$", "") + "/" + key;
    }

    @Override
    public void delete(String url) {
        validateConfiguration();
        CampaignThumbnailUrl.managedKey(properties.publicBaseUrl(), url).ifPresent(key -> {
            DeleteObjectRequest request = DeleteObjectRequest.builder()
                    .bucket(properties.bucket())
                    .key(key)
                    .build();
            try {
                s3Client.deleteObject(request);
            } catch (SdkException e) {
                throw new BusinessException(ErrorCode.MEDIA_DELETE_FAILED, e);
            }
        });
    }

    private void validateConfiguration() {
        if (properties.bucket() == null || properties.bucket().isBlank()
                || properties.publicBaseUrl() == null || properties.publicBaseUrl().isBlank()) {
            throw new BusinessException(ErrorCode.MEDIA_STORAGE_CONFIG_MISSING);
        }
    }
}
