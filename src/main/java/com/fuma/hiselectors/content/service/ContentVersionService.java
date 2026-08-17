package com.fuma.hiselectors.content.service;

import com.fuma.hiselectors.content.model.Content;
import com.fuma.hiselectors.content.model.ContentMedia;
import com.fuma.hiselectors.content.model.ContentVersion;
import com.fuma.hiselectors.content.model.MediaType;
import com.fuma.hiselectors.content.repository.ContentMediaRepository;
import com.fuma.hiselectors.content.repository.ContentRepository;
import com.fuma.hiselectors.content.repository.ContentVersionRepository;
import com.fuma.hiselectors.exception.BusinessException;
import com.fuma.hiselectors.exception.ErrorCode;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ContentVersionService {

    private final ContentRepository contentRepository;
    private final ContentVersionRepository contentVersionRepository;
    private final ContentMediaRepository contentMediaRepository;

    @Transactional
    public VersionCreationResult createVersion(Long contentId, String contentHash,
                                               List<MediaInput> mediaInputs) {
        Content content = contentRepository.findByIdForUpdate(contentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CONTENT_NOT_FOUND));
        return contentVersionRepository.findByContentIdAndContentHash(contentId, contentHash)
                .map(existing -> new VersionCreationResult(existing.getId(), false))
                .orElseGet(() -> create(content, contentHash, mediaInputs));
    }

    private VersionCreationResult create(Content content, String contentHash,
                                         List<MediaInput> mediaInputs) {
        ContentVersion version = contentVersionRepository.save(ContentVersion.create(
                content.getId(), content.nextVersionNo(), contentHash));
        List<ContentMedia> media = mediaInputs.stream()
                .map(input -> ContentMedia.create(
                        version.getId(), input.mediaUrl(), input.mediaType(), input.body()))
                .toList();
        contentMediaRepository.saveAll(media);
        return new VersionCreationResult(version.getId(), true);
    }

    public record MediaInput(String mediaUrl, MediaType mediaType, Map<String, Object> body) {
    }

    public record VersionCreationResult(Long contentVersionId, boolean created) {
    }
}
