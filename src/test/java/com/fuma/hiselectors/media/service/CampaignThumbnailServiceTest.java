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
import com.fuma.hiselectors.media.dto.CampaignThumbnailUploadResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;
import software.amazon.awssdk.services.s3.model.S3Exception;

class CampaignThumbnailServiceTest {

    private static final byte[] PNG = {
            (byte) 0x89, 'P', 'N', 'G', 0x0d, 0x0a, 0x1a, 0x0a, 0x00
    };

    private S3Client s3Client;
    private CampaignThumbnailService service;

    @BeforeEach
    void setUp() {
        s3Client = mock(S3Client.class);
        S3MediaProperties properties = new S3MediaProperties(
                "ap-northeast-2", "hiselectors-media", "https://media.hiselectors.shop/");
        service = new CampaignThumbnailService(new S3CampaignThumbnailStorage(s3Client, properties));
    }

    @Test
    void uploadsValidatedImageWithImmutableCacheHeaders() {
        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenReturn(PutObjectResponse.builder().eTag("etag").build());
        MockMultipartFile file = new MockMultipartFile(
                "file", "campaign.png", "image/png", PNG);

        CampaignThumbnailUploadResponse response = service.upload(file);

        ArgumentCaptor<PutObjectRequest> requestCaptor = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(s3Client).putObject(requestCaptor.capture(), any(RequestBody.class));
        PutObjectRequest request = requestCaptor.getValue();
        assertThat(request.bucket()).isEqualTo("hiselectors-media");
        assertThat(request.key()).startsWith("campaigns/").endsWith(".png");
        assertThat(request.contentType()).isEqualTo("image/png");
        assertThat(request.cacheControl()).isEqualTo("public, max-age=31536000, immutable");
        assertThat(response.url()).isEqualTo("https://media.hiselectors.shop/" + request.key());
    }

    @Test
    void rejectsContentWhoseSignatureDoesNotMatchTheClaimedImageType() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "not-an-image.png", "image/png", "plain text".getBytes());

        assertThatThrownBy(() -> service.upload(file))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("이미지 파일 형식이 올바르지 않습니다.");
        verify(s3Client, never()).putObject(any(PutObjectRequest.class), any(RequestBody.class));
    }

    @Test
    void rejectsFilesLargerThanFiveMegabytes() {
        byte[] oversized = new byte[(int) CampaignThumbnailService.MAX_FILE_SIZE + 1];
        MockMultipartFile file = new MockMultipartFile(
                "file", "large.png", "image/png", oversized);

        assertThatThrownBy(() -> service.upload(file))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("썸네일 파일은 5MB 이하여야 합니다.");
        verify(s3Client, never()).putObject(any(PutObjectRequest.class), any(RequestBody.class));
    }

    @Test
    void mapsS3FailureToMediaUploadBusinessError() {
        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenThrow(S3Exception.builder().message("unavailable").build());
        MockMultipartFile file = new MockMultipartFile(
                "file", "campaign.png", "image/png", PNG);

        assertThatThrownBy(() -> service.upload(file))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.MEDIA_UPLOAD_FAILED));
    }
}
