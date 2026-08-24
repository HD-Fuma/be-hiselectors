package com.fuma.hiselectors.media.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fuma.hiselectors.exception.BusinessException;
import com.fuma.hiselectors.exception.ErrorCode;
import com.fuma.hiselectors.media.config.S3MediaProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectResponse;
import software.amazon.awssdk.services.s3.model.S3Exception;

class S3CampaignThumbnailStorageTest {

    private S3Client s3Client;
    private S3CampaignThumbnailStorage storage;

    @BeforeEach
    void setUp() {
        s3Client = mock(S3Client.class);
        storage = new S3CampaignThumbnailStorage(s3Client, new S3MediaProperties(
                "ap-northeast-2", "hiselectors-media", "https://media.hiselectors.shop/"));
    }

    @Test
    void deletesManagedCampaignThumbnailObject() {
        when(s3Client.deleteObject(any(DeleteObjectRequest.class)))
                .thenReturn(DeleteObjectResponse.builder().build());

        storage.delete("https://media.hiselectors.shop/campaigns/123e4567-e89b-12d3-a456-426614174000.webp");

        ArgumentCaptor<DeleteObjectRequest> request = ArgumentCaptor.forClass(DeleteObjectRequest.class);
        verify(s3Client).deleteObject(request.capture());
        assertThat(request.getValue().bucket()).isEqualTo("hiselectors-media");
        assertThat(request.getValue().key())
                .isEqualTo("campaigns/123e4567-e89b-12d3-a456-426614174000.webp");
    }

    @Test
    void skipsExternalAndMalformedThumbnailUrls() {
        storage.delete("https://external.example/campaigns/123e4567-e89b-12d3-a456-426614174000.png");
        storage.delete("https://media.hiselectors.shop/campaigns/../secret.png");
        storage.delete("https://media.hiselectors.shop/campaigns/not-a-uuid.png");
        storage.delete("https://media.hiselectors.shop/campaigns/123e4567-e89b-12d3-a456-426614174000.gif");

        verify(s3Client, never()).deleteObject(any(DeleteObjectRequest.class));
    }

    @Test
    void mapsS3DeleteFailureToMediaDeleteBusinessError() {
        RuntimeException cause = S3Exception.builder().message("unavailable").build();
        when(s3Client.deleteObject(any(DeleteObjectRequest.class)))
                .thenThrow(cause);

        assertThatThrownBy(() -> storage.delete(
                "https://media.hiselectors.shop/campaigns/123e4567-e89b-12d3-a456-426614174000.jpg"))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> {
                            assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.MEDIA_DELETE_FAILED);
                            assertThat(exception.getCause()).isSameAs(cause);
                        });
    }

    @Test
    void reportsMissingConfigurationBeforeCheckingDeleteUrl() {
        S3CampaignThumbnailStorage misconfigured = new S3CampaignThumbnailStorage(s3Client,
                new S3MediaProperties("ap-northeast-2", "hiselectors-media", " "));

        assertThatThrownBy(() -> misconfigured.delete("https://external.example/thumbnail.png"))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.MEDIA_STORAGE_CONFIG_MISSING));
    }
}
