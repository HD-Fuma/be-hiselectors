package com.fuma.hiselectors.content.service;

import com.fuma.hiselectors.content.client.dto.RawContent;
import com.fuma.hiselectors.content.client.dto.RawContentMedia;
import com.fuma.hiselectors.content.model.ContentMedia;
import com.fuma.hiselectors.content.model.ContentVersion;
import com.fuma.hiselectors.content.model.ContentVersionCreationReason;
import com.fuma.hiselectors.content.model.MediaType;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/** 콘텐츠 버전 해시 및 미디어 저장 구조 생성 */
@Component
public class ContentSnapshotFactory {

    ContentVersion createVersion(
            Long contentId,
            Long versionNo,
            RawContent rawContent,
            LocalDateTime createdAt,
            ContentVersionCreationReason creationReason) {
        return ContentVersion.create(
                contentId, versionNo, contentHash(rawContent), creationReason, createdAt);
    }

    List<ContentMedia> createMedia(Long versionId, RawContent rawContent) {
        List<ContentMedia> media = new ArrayList<>();
        int sequenceNo = 0;
        for (String text : rawContent.texts()) {
            media.add(ContentMedia.create(
                    versionId, MediaType.TEXT, null, null, sequenceNo++,
                    Map.of("text", text)));
        }
        for (RawContentMedia rawMedia : rawContent.media()) {
            if (rawMedia.mediaType() == RawContentMedia.MediaType.TEXT) {
                throw new IllegalStateException(
                        "RawContentMedia.TEXT는 본문을 제공하지 않아 저장할 수 없습니다.");
            }
            media.add(ContentMedia.create(
                    versionId,
                    MediaType.valueOf(rawMedia.mediaType().name()),
                    rawMedia.mediaUrl(),
                    thumbnailUrl(rawMedia),
                    rawMedia.snsMediaId(),
                    sequenceNo++,
                    Map.of()));
        }
        return media;
    }

    String thumbnailUrl(RawContentMedia media) {
        return media.thumbnailUrls().isEmpty() ? null : media.thumbnailUrls().getLast();
    }

    String contentHash(RawContent rawContent) {
        StringBuilder canonical = new StringBuilder();
        appendHashValue(canonical, "content-hash-v2");
        appendHashValue(canonical, "texts");
        appendHashValue(canonical, String.valueOf(rawContent.texts().size()));
        for (int sequenceNo = 0; sequenceNo < rawContent.texts().size(); sequenceNo++) {
            appendHashValue(canonical, String.valueOf(sequenceNo));
            appendHashValue(canonical, rawContent.texts().get(sequenceNo));
        }

        appendHashValue(canonical, "assets");
        appendHashValue(canonical, String.valueOf(rawContent.media().size()));
        int sequenceNo = rawContent.texts().size();
        for (RawContentMedia media : rawContent.media()) {
            if (media.snsMediaId() == null) {
                throw new IllegalStateException("미디어 SNS ID는 비어 있을 수 없습니다.");
            }
            appendHashValue(canonical, String.valueOf(sequenceNo++));
            appendHashValue(canonical, media.mediaType().name());
            appendHashValue(canonical, media.snsMediaId());
        }

        try {
            byte[] value = canonical.toString().getBytes(StandardCharsets.UTF_8);
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(value));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256을 사용할 수 없습니다.", exception);
        }
    }

    private void appendHashValue(StringBuilder target, String value) {
        target.append(value.length()).append(':').append(value);
    }
}
