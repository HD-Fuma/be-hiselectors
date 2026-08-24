package com.fuma.hiselectors.media.service;

import com.fuma.hiselectors.exception.BusinessException;
import com.fuma.hiselectors.exception.ErrorCode;
import com.fuma.hiselectors.media.config.S3MediaProperties;
import com.fuma.hiselectors.media.dto.CampaignThumbnailUploadResponse;
import java.io.IOException;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

@Service
@RequiredArgsConstructor
public class CampaignThumbnailService {

    static final long MAX_FILE_SIZE = 5L * 1024 * 1024;
    private static final String CACHE_CONTROL = "public, max-age=31536000, immutable";
    private static final Map<String, String> EXTENSIONS = Map.of(
            "image/jpeg", "jpg",
            "image/png", "png",
            "image/webp", "webp"
    );

    private final S3Client s3Client;
    private final S3MediaProperties properties;

    public CampaignThumbnailUploadResponse upload(MultipartFile file) {
        byte[] bytes = readAndValidate(file);
        String contentType = file.getContentType().toLowerCase(Locale.ROOT);
        validateConfiguration();

        String key = "campaigns/" + UUID.randomUUID() + "." + EXTENSIONS.get(contentType);
        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(properties.bucket())
                .key(key)
                .contentType(contentType)
                .cacheControl(CACHE_CONTROL)
                .build();
        try {
            s3Client.putObject(request, RequestBody.fromBytes(bytes));
        } catch (SdkException e) {
            throw new BusinessException(ErrorCode.MEDIA_UPLOAD_FAILED);
        }

        return new CampaignThumbnailUploadResponse(publicUrl(key));
    }

    private byte[] readAndValidate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("썸네일 파일을 선택해주세요.");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new IllegalArgumentException("썸네일 파일은 5MB 이하여야 합니다.");
        }
        String contentType = file.getContentType();
        if (contentType == null || !EXTENSIONS.containsKey(contentType.toLowerCase(Locale.ROOT))) {
            throw new IllegalArgumentException("썸네일은 JPG, PNG 또는 WEBP 파일만 업로드할 수 있습니다.");
        }

        try {
            byte[] bytes = file.getBytes();
            if (!hasValidSignature(contentType.toLowerCase(Locale.ROOT), bytes)) {
                throw new IllegalArgumentException("이미지 파일 형식이 올바르지 않습니다.");
            }
            return bytes;
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.MEDIA_UPLOAD_FAILED);
        }
    }

    private boolean hasValidSignature(String contentType, byte[] bytes) {
        return switch (contentType) {
            case "image/jpeg" -> bytes.length >= 3
                    && unsigned(bytes[0]) == 0xff && unsigned(bytes[1]) == 0xd8
                    && unsigned(bytes[2]) == 0xff;
            case "image/png" -> bytes.length >= 8
                    && unsigned(bytes[0]) == 0x89 && bytes[1] == 'P' && bytes[2] == 'N' && bytes[3] == 'G'
                    && unsigned(bytes[4]) == 0x0d && unsigned(bytes[5]) == 0x0a
                    && unsigned(bytes[6]) == 0x1a && unsigned(bytes[7]) == 0x0a;
            case "image/webp" -> bytes.length >= 12
                    && bytes[0] == 'R' && bytes[1] == 'I' && bytes[2] == 'F' && bytes[3] == 'F'
                    && bytes[8] == 'W' && bytes[9] == 'E' && bytes[10] == 'B' && bytes[11] == 'P';
            default -> false;
        };
    }

    private int unsigned(byte value) {
        return Byte.toUnsignedInt(value);
    }

    private void validateConfiguration() {
        if (properties.bucket() == null || properties.bucket().isBlank()
                || properties.publicBaseUrl() == null || properties.publicBaseUrl().isBlank()) {
            throw new BusinessException(ErrorCode.MEDIA_STORAGE_CONFIG_MISSING);
        }
    }

    private String publicUrl(String key) {
        String baseUrl = properties.publicBaseUrl().replaceAll("/+$", "");
        return baseUrl + "/" + key;
    }
}
