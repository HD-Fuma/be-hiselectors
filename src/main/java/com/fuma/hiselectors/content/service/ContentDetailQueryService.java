package com.fuma.hiselectors.content.service;

import com.fuma.hiselectors.content.dto.ContentDetailResponse;
import com.fuma.hiselectors.content.dto.ContentReportResponse;
import com.fuma.hiselectors.content.dto.ContentVersionDetailResponse;
import com.fuma.hiselectors.content.dto.ContentVersionSummaryResponse;
import com.fuma.hiselectors.content.dto.ContentVersionMediaResponse;
import com.fuma.hiselectors.content.model.Content;
import com.fuma.hiselectors.content.model.ContentMedia;
import com.fuma.hiselectors.content.model.ContentReport;
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
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ContentDetailQueryService {

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
        boolean historicalVersion = selected.getVersionNo() < versions.getFirst().getVersionNo();

        return new ContentDetailResponse(
                content.getId(),
                content.getSelectorsId(),
                content.getSnsCode(),
                content.getSnsContentId(),
                content.getContentUrl(),
                content.getContentType(),
                content.getCreatedAt(),
                versions.stream().map(this::toSummary).toList(),
                toDetail(content.getId(), selected, historicalVersion));
    }

    private ContentVersionSummaryResponse toSummary(ContentVersion version) {
        return new ContentVersionSummaryResponse(
                version.getId(),
                version.getVersionNo(),
                version.getCreationReason(),
                version.getStatus(),
                version.getInspectionDecision(),
                version.getCreatedAt(),
                version.getInspectedAt());
    }

    private ContentVersionDetailResponse toDetail(
            Long contentId, ContentVersion version, boolean historicalVersion) {
        List<ContentMedia> media = contentMediaRepository
                .findByContentVersionIdOrderBySequenceNoAsc(version.getId());
        ContentReport latestReport = contentReportRepository
                .findFirstByContentVersionIdOrderByIdDesc(version.getId())
                .orElse(null);
        ContentReportResponse report = latestReport == null ? null : new ContentReportResponse(
                latestReport.getId(),
                latestReport.getSummary(),
                latestReport.getPurpose(),
                latestReport.getFlow(),
                latestReport.getOverallAssessment());

        return new ContentVersionDetailResponse(
                version.getId(),
                version.getVersionNo(),
                version.getCreationReason(),
                version.getStatus(),
                version.getInspectionDecision(),
                version.getCreatedAt(),
                version.getInspectedAt(),
                media.stream().map(ContentVersionMediaResponse::from).toList(),
                report,
                findViolations(contentId, version.getId(), historicalVersion,
                        latestReport == null ? null : latestReport.getInspectionPolicyId()));
    }

    private List<ContentViolationResponse> findViolations(
            Long contentId, Long contentVersionId, boolean historicalVersion,
            Long inspectionPolicyId) {
        if (!historicalVersion && inspectionPolicyId == null) {
            return List.of();
        }
        List<ViolationEvidenceHistory> histories = new java.util.ArrayList<>(historicalVersion
                ? evidenceHistoryRepository
                        .findAllByContentVersionIdOrderByDetectedAtAscIdAsc(contentVersionId)
                : evidenceHistoryRepository
                        .findAllByContentVersionIdAndInspectionPolicyIdOrderByIdAsc(
                                contentVersionId, inspectionPolicyId));
        List<ViolationItem> correctionCandidates = historicalVersion
                ? List.of()
                : violationItemRepository
                        .findAllByResolvedContentVersionIdAndStatusOrderByIdAsc(
                                contentVersionId,
                                ViolationStatus.CORRECTION_REVIEW_PENDING);
        java.util.Set<Long> historyItemIds = histories.stream()
                .map(ViolationEvidenceHistory::getViolationItemId)
                .collect(java.util.stream.Collectors.toSet());
        correctionCandidates.stream()
                .filter(item -> !historyItemIds.contains(item.getId()))
                .map(item -> evidenceHistoryRepository
                        .findFirstByViolationItemIdOrderByDetectedAtDescIdDesc(item.getId()))
                .flatMap(java.util.Optional::stream)
                .forEach(histories::add);
        if (histories.isEmpty() && correctionCandidates.isEmpty()) {
            return List.of();
        }
        Map<Long, ViolationItem> itemById = new java.util.LinkedHashMap<>();
        correctionCandidates.stream()
                .filter(item -> contentId.equals(item.getContentId()))
                .forEach(item -> itemById.put(item.getId(), item));
        violationItemRepository.findAllById(histories.stream()
                        .map(ViolationEvidenceHistory::getViolationItemId).distinct().toList())
                .stream()
                .filter(item -> contentId.equals(item.getContentId()))
                .forEach(item -> itemById.put(item.getId(), item));
        Map<Long, ViolationType> typeById = violationTypeRepository.findAllById(
                        itemById.values().stream()
                                .map(ViolationItem::getViolationTypeId).toList())
                .stream()
                .collect(java.util.stream.Collectors.toMap(
                        ViolationType::getId, type -> type));

        return histories.stream()
                .filter(history -> itemById.containsKey(history.getViolationItemId()))
                .map(history -> {
                    ViolationItem item = itemById.get(history.getViolationItemId());
                    ViolationType type = typeById.get(item.getViolationTypeId());
                    return new ContentViolationResponse(
                            item.getId(),
                            history.getId(),
                            history.getInspectionPolicyId(),
                            type == null ? null : type.getCode(),
                            type == null ? null : type.getDescription(),
                            item.getStatus(),
                            history.getEvidence(),
                            history.getDetectedAt());
                })
                .toList();
    }
}
