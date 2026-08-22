package com.fuma.hiselectors.content.service;

import com.fuma.hiselectors.content.dto.ContentDetailResponse;
import com.fuma.hiselectors.content.dto.ContentReportResponse;
import com.fuma.hiselectors.content.dto.ContentVersionDetailResponse;
import com.fuma.hiselectors.content.dto.ContentVersionSummaryResponse;
import com.fuma.hiselectors.content.model.Content;
import com.fuma.hiselectors.content.model.ContentMedia;
import com.fuma.hiselectors.content.model.ContentVersion;
import com.fuma.hiselectors.content.repository.ContentMediaRepository;
import com.fuma.hiselectors.content.repository.ContentReportRepository;
import com.fuma.hiselectors.content.repository.ContentRepository;
import com.fuma.hiselectors.content.repository.ContentVersionRepository;
import com.fuma.hiselectors.exception.BusinessException;
import com.fuma.hiselectors.exception.ErrorCode;
import com.fuma.hiselectors.inspection.dto.ContentViolationResponse;
import com.fuma.hiselectors.inspection.model.ViolationEvidence;
import com.fuma.hiselectors.inspection.model.ViolationEvidenceHistory;
import com.fuma.hiselectors.inspection.model.ViolationItem;
import com.fuma.hiselectors.inspection.model.ViolationStatus;
import com.fuma.hiselectors.inspection.model.ViolationType;
import com.fuma.hiselectors.inspection.repository.ViolationEvidenceHistoryRepository;
import com.fuma.hiselectors.inspection.repository.ViolationItemRepository;
import com.fuma.hiselectors.inspection.repository.ViolationTypeRepository;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ContentDetailQueryService {

    private static final List<ViolationStatus> OPEN_STATUSES = List.of(
            ViolationStatus.PENDING,
            ViolationStatus.VIOLATION_CONFIRMED,
            ViolationStatus.EDIT_REQUESTED);

    private final ContentRepository contentRepository;
    private final ContentVersionRepository contentVersionRepository;
    private final ContentMediaRepository contentMediaRepository;
    private final ContentReportRepository contentReportRepository;
    private final ViolationItemRepository violationItemRepository;
    private final ViolationTypeRepository violationTypeRepository;
    private final ViolationEvidenceHistoryRepository evidenceHistoryRepository;

    public ContentDetailResponse getLatest(Long contentId) {
        return get(contentId, null);
    }

    public ContentDetailResponse getVersion(Long contentId, Long contentVersionId) {
        return get(contentId, contentVersionId);
    }

    private ContentDetailResponse get(Long contentId, Long requestedVersionId) {
        Content content = contentRepository.findById(contentId)
                .filter(candidate -> !candidate.isDeleted())
                .orElseThrow(() -> new BusinessException(ErrorCode.CONTENT_NOT_FOUND));
        List<ContentVersion> versions = contentVersionRepository
                .findAllByContentIdOrderByVersionNoDesc(contentId);
        if (versions.isEmpty()) {
            throw new BusinessException(ErrorCode.CONTENT_VERSION_NOT_FOUND);
        }

        ContentVersion selected = requestedVersionId == null
                ? versions.getFirst()
                : contentVersionRepository.findByIdAndContentId(
                        requestedVersionId, contentId)
                        .orElseThrow(() -> new BusinessException(
                                ErrorCode.CONTENT_VERSION_NOT_FOUND));

        return new ContentDetailResponse(
                content.getId(),
                content.getSelectorsId(),
                content.getSnsCode(),
                content.getSnsContentId(),
                content.getContentUrl(),
                content.getContentType(),
                content.getCreatedAt(),
                versions.stream().map(this::toSummary).toList(),
                toDetail(content.getId(), selected));
    }

    private ContentVersionSummaryResponse toSummary(ContentVersion version) {
        return new ContentVersionSummaryResponse(
                version.getId(),
                version.getVersionNo(),
                version.getStatus(),
                version.getCreatedAt(),
                version.getInspectedAt());
    }

    private ContentVersionDetailResponse toDetail(Long contentId, ContentVersion version) {
        List<ContentMedia> media = contentMediaRepository
                .findByContentVersionIdOrderBySequenceNoAsc(version.getId());
        ContentReportResponse report = contentReportRepository
                .findFirstByContentVersionIdOrderByIdDesc(version.getId())
                .map(contentReport -> new ContentReportResponse(
                        contentReport.getId(),
                        contentReport.getSummary(),
                        contentReport.getPurpose(),
                        contentReport.getFlow(),
                        contentReport.getOverallAssessment()))
                .orElse(null);

        return new ContentVersionDetailResponse(
                version.getId(),
                version.getVersionNo(),
                version.getStatus(),
                version.getCreatedAt(),
                version.getInspectedAt(),
                media.stream()
                        .filter(mediaItem -> mediaItem.getMediaType()
                                == com.fuma.hiselectors.content.model.MediaType.TEXT)
                        .map(ContentDetailQueryService::textOf)
                        .filter(java.util.Objects::nonNull)
                        .toList(),
                report,
                findViolations(contentId, version.getId()));
    }

    private List<ContentViolationResponse> findViolations(
            Long contentId, Long contentVersionId) {
        List<ViolationItem> items = violationItemRepository
                .findAllByContentIdAndStatusInOrderByIdAsc(
                        contentId, OPEN_STATUSES);
        if (items.isEmpty()) {
            return List.of();
        }

        List<Long> itemIds = items.stream().map(ViolationItem::getId).toList();
        Map<Long, ViolationEvidence> evidenceByItemId = evidenceHistoryRepository
                .findAllByContentVersionIdAndViolationItemIdIn(contentVersionId, itemIds)
                .stream()
                .collect(java.util.stream.Collectors.toMap(
                        ViolationEvidenceHistory::getViolationItemId,
                        ViolationEvidenceHistory::getEvidence,
                        (first, ignored) -> first,
                        LinkedHashMap::new));
        Map<Long, ViolationType> typeById = violationTypeRepository.findAllById(
                        items.stream().map(ViolationItem::getViolationTypeId).toList())
                .stream()
                .collect(java.util.stream.Collectors.toMap(
                        ViolationType::getId, type -> type));

        return items.stream()
                .filter(item -> evidenceByItemId.containsKey(item.getId())
                        || contentVersionId.equals(item.getLastDetectedContentVersionId()))
                .map(item -> {
                    ViolationType type = typeById.get(item.getViolationTypeId());
                    ViolationEvidence evidence = evidenceByItemId.getOrDefault(
                            item.getId(), item.getEvidence());
                    return new ContentViolationResponse(
                            item.getId(),
                            type == null ? null : type.getCode(),
                            type == null ? null : type.getDescription(),
                            item.getStatus(),
                            evidence);
                })
                .toList();
    }

    private static String textOf(ContentMedia media) {
        Object text = media.bodyOrEmpty().get("text");
        return text instanceof String value ? value : null;
    }
}
