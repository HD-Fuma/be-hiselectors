package com.fuma.hiselectors.media.service;

import com.fuma.hiselectors.exception.BusinessException;
import com.fuma.hiselectors.exception.ErrorCode;
import com.fuma.hiselectors.media.dto.CampaignThumbnailUploadResponse;
import java.io.IOException;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class CampaignThumbnailService {

    static final long MAX_FILE_SIZE = 5L * 1024 * 1024;
    private static final Map<String, String> EXTENSIONS = Map.of(
            "image/jpeg", "jpg",
            "image/png", "png",
            "image/webp", "webp"
    );

    private final CampaignThumbnailStorage storage;

    public CampaignThumbnailUploadResponse upload(MultipartFile file) {
        byte[] bytes = readAndValidate(file);
        String contentType = file.getContentType().toLowerCase(Locale.ROOT);
        String key = "campaigns/" + UUID.randomUUID() + "." + EXTENSIONS.get(contentType);
        return new CampaignThumbnailUploadResponse(storage.store(key, contentType, bytes));
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

}
