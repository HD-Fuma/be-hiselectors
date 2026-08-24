package com.fuma.hiselectors.media.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.fuma.hiselectors.media.config.LocalMediaProperties;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LocalCampaignThumbnailStorageTest {

    @TempDir
    Path directory;

    @Test
    void storesThumbnailUnderTheConfiguredLocalMediaDirectory() throws Exception {
        LocalCampaignThumbnailStorage storage = new LocalCampaignThumbnailStorage(
                new LocalMediaProperties(directory.toString(), "http://127.0.0.1:8080/media/"));
        byte[] bytes = {(byte) 0x89, 'P', 'N', 'G'};

        String url = storage.store("campaigns/thumbnail.png", "image/png", bytes);

        assertThat(Files.readAllBytes(directory.resolve("campaigns/thumbnail.png"))).isEqualTo(bytes);
        assertThat(url).isEqualTo("http://127.0.0.1:8080/media/campaigns/thumbnail.png");
    }

    @Test
    void deletesManagedCampaignThumbnail() throws Exception {
        LocalCampaignThumbnailStorage storage = new LocalCampaignThumbnailStorage(
                new LocalMediaProperties(directory.toString(), "http://127.0.0.1:8080/media/"));
        Path thumbnail = directory.resolve("campaigns/123e4567-e89b-12d3-a456-426614174000.png");
        Files.createDirectories(thumbnail.getParent());
        Files.write(thumbnail, new byte[] {1, 2, 3});

        storage.delete("http://127.0.0.1:8080/media/campaigns/123e4567-e89b-12d3-a456-426614174000.png");

        assertThat(thumbnail).doesNotExist();
    }

    @Test
    void skipsExternalAndMalformedThumbnailUrls() throws Exception {
        LocalCampaignThumbnailStorage storage = new LocalCampaignThumbnailStorage(
                new LocalMediaProperties(directory.toString(), "http://127.0.0.1:8080/media/"));
        Path thumbnail = directory.resolve("campaigns/123e4567-e89b-12d3-a456-426614174000.png");
        Files.createDirectories(thumbnail.getParent());
        Files.write(thumbnail, new byte[] {1, 2, 3});

        storage.delete("https://external.example/campaigns/123e4567-e89b-12d3-a456-426614174000.png");
        storage.delete("http://127.0.0.1:8080/media/campaigns/../secret.png");
        storage.delete("http://127.0.0.1:8080/media/campaigns/not-a-uuid.png");

        assertThat(thumbnail).exists();
    }
}
