package com.fuma.hiselectors.media.service;

import com.fuma.hiselectors.exception.BusinessException;
import com.fuma.hiselectors.exception.ErrorCode;
import com.fuma.hiselectors.media.config.LocalMediaProperties;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("local")
@RequiredArgsConstructor
public class LocalCampaignThumbnailStorage implements CampaignThumbnailStorage {

    private final LocalMediaProperties properties;

    @Override
    public String store(String key, String contentType, byte[] bytes) {
        Path root = Path.of(properties.directory()).toAbsolutePath().normalize();
        Path target = root.resolve(key).normalize();
        if (!target.startsWith(root)) {
            throw new BusinessException(ErrorCode.MEDIA_UPLOAD_FAILED);
        }
        try {
            Files.createDirectories(target.getParent());
            Files.write(target, bytes);
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.MEDIA_UPLOAD_FAILED);
        }
        return properties.publicBaseUrl().replaceAll("/+$", "") + "/" + key;
    }

    @Override
    public void delete(String url) {
        CampaignThumbnailUrl.managedKey(properties.publicBaseUrl(), url).ifPresent(key -> {
            Path root = Path.of(properties.directory()).toAbsolutePath().normalize();
            Path target = root.resolve(key).normalize();
            if (!target.startsWith(root)) {
                return;
            }
            try {
                Files.deleteIfExists(target);
            } catch (IOException e) {
                throw new BusinessException(ErrorCode.MEDIA_DELETE_FAILED);
            }
        });
    }
}
