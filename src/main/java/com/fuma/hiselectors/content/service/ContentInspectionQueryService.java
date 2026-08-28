package com.fuma.hiselectors.content.service;

import com.fuma.hiselectors.content.dto.ContentInspectionListItemResponse;
import com.fuma.hiselectors.content.dto.ContentInspectionListType;
import com.fuma.hiselectors.content.dto.ContentInspectionMediaResponse;
import com.fuma.hiselectors.content.dto.ContentInspectionQueryRow;
import com.fuma.hiselectors.content.model.ContentMedia;
import com.fuma.hiselectors.content.model.ContentInspectionDecision;
import com.fuma.hiselectors.content.model.ContentVersionCreationReason;
import com.fuma.hiselectors.content.model.ContentVersionStatus;
import com.fuma.hiselectors.content.model.MediaType;
import com.fuma.hiselectors.content.repository.ContentMediaRepository;
import com.fuma.hiselectors.content.repository.ContentRepository;
import com.fuma.hiselectors.generation.model.Generation;
import com.fuma.hiselectors.generation.service.GenerationService;
import com.fuma.hiselectors.inspection.model.ViolationStatus;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ContentInspectionQueryService {

    private static final java.util.Set<ViolationStatus> OPEN_VIOLATION_STATUSES =
            EnumSet.of(
                    ViolationStatus.PENDING,
                    ViolationStatus.VIOLATION_CONFIRMED,
                    ViolationStatus.EDIT_REQUESTED,
                    ViolationStatus.CORRECTION_REVIEW_PENDING);

    private final GenerationService generationService;
    private final ContentRepository contentRepository;
    private final ContentMediaRepository mediaRepository;

    public Page<ContentInspectionListItemResponse> getCurrentGenerationContents(
            int page, int size, ContentInspectionListType tab) {
        Generation generation = generationService.getCurrentActivity();
        Page<ContentInspectionQueryRow> rows = contentRepository
                .findInspectionRowsByGenerationId(
                        generation.getId(), tab.name(), OPEN_VIOLATION_STATUSES,
                        ContentVersionStatus.COMPLETED,
                        ViolationStatus.PENDING,
                        ContentVersionCreationReason.SOURCE_CHANGE,
                        ContentInspectionDecision.REJECTED,
                        PageRequest.of(page, size));
        if (rows.isEmpty()) {
            return rows.map(row -> toResponse(row, generation.getGenerationName(), List.of()));
        }

        List<Long> versionIds = rows.getContent().stream()
                .map(ContentInspectionQueryRow::latestVersionId)
                .toList();
        Map<Long, List<ContentMedia>> mediaByVersionId = new LinkedHashMap<>();
        for (ContentMedia media : mediaRepository
                .findAllByContentVersionIdInOrderByContentVersionIdAscSequenceNoAsc(versionIds)) {
            mediaByVersionId.computeIfAbsent(media.getContentVersionId(), ignored ->
                    new java.util.ArrayList<>()).add(media);
        }

        return rows.map(row -> toResponse(
                row,
                generation.getGenerationName(),
                mediaByVersionId.getOrDefault(row.latestVersionId(), List.of())));
    }

    private ContentInspectionListItemResponse toResponse(
            ContentInspectionQueryRow row,
            String generationName,
            List<ContentMedia> storedMedia) {
        List<String> texts = storedMedia.stream()
                .filter(media -> media.getMediaType() == MediaType.TEXT)
                .map(media -> media.bodyOrEmpty().get("text"))
                .filter(String.class::isInstance)
                .map(String.class::cast)
                .toList();
        List<ContentInspectionMediaResponse> media = storedMedia.stream()
                .filter(stored -> stored.getMediaType() != MediaType.TEXT)
                .map(ContentInspectionMediaResponse::from)
                .toList();

        return new ContentInspectionListItemResponse(
                row.contentId(),
                row.selectorsId(),
                row.selectorsNickname(),
                row.snsCode(),
                row.snsContentId(),
                row.contentUrl(),
                row.contentType(),
                row.storedAt(),
                row.latestVersionId(),
                row.latestVersionNo(),
                row.inspectionDecision() != null
                        ? row.inspectionDecision().name()
                        : row.inspectionStatus() == null ? null : row.inspectionStatus().name(),
                row.inspectedAt(),
                row.latestVersionStoredAt(),
                row.accountId(),
                row.profileImageUrl(),
                generationName,
                texts,
                media);
    }
}
