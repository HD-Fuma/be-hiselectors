package com.fuma.hiselectors.content.service;

import com.fuma.hiselectors.content.dto.ContentInspectionConfirmationRequest;
import com.fuma.hiselectors.content.dto.ContentInspectionConfirmationRequest.ViolationDecision;
import com.fuma.hiselectors.content.dto.ContentInspectionConfirmationResponse;
import com.fuma.hiselectors.content.model.Content;
import com.fuma.hiselectors.content.model.ContentInspectionDecision;
import com.fuma.hiselectors.content.model.ContentReport;
import com.fuma.hiselectors.content.model.ContentVersion;
import com.fuma.hiselectors.content.repository.ContentReportRepository;
import com.fuma.hiselectors.content.repository.ContentRepository;
import com.fuma.hiselectors.content.repository.ContentVersionRepository;
import com.fuma.hiselectors.exception.BusinessException;
import com.fuma.hiselectors.exception.ErrorCode;
import com.fuma.hiselectors.inspection.model.ViolationEvidenceHistory;
import com.fuma.hiselectors.inspection.model.ViolationItem;
import com.fuma.hiselectors.inspection.model.ViolationStatus;
import com.fuma.hiselectors.inspection.repository.ViolationEvidenceHistoryRepository;
import com.fuma.hiselectors.inspection.repository.ViolationItemRepository;
import com.fuma.hiselectors.penalty.service.PenaltyService;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ContentInspectionConfirmationService {

    private static final Set<ViolationStatus> ALLOWED_TARGET_STATUSES = Set.of(
            ViolationStatus.VIOLATION_CONFIRMED,
            ViolationStatus.DISMISSED);

    private final ContentRepository contentRepository;
    private final ContentVersionRepository contentVersionRepository;
    private final ContentReportRepository contentReportRepository;
    private final ViolationEvidenceHistoryRepository historyRepository;
    private final ViolationItemRepository violationItemRepository;
    private final PenaltyService penaltyService;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public ContentInspectionConfirmationResponse confirm(
            Long contentId,
            Long contentVersionId,
            ContentInspectionConfirmationRequest request,
            String adminLoginId) {
        Content content = requireContent(contentId);
        ContentVersion version = requireOwnedVersionForUpdate(contentId, contentVersionId);
        if (version.getInspectionDecision() != null) {
            throw new BusinessException(ErrorCode.CONTENT_INSPECTION_ALREADY_CONFIRMED);
        }

        validateRequestStatuses(request);
        List<ViolationItem> versionItems = latestReportItemsForUpdate(contentVersionId);
        List<ViolationItem> pendingItems = versionItems.stream()
                .filter(item -> item.getStatus() == ViolationStatus.PENDING)
                .toList();

        if (!versionItems.isEmpty() && pendingItems.isEmpty()) {
            throw new BusinessException(ErrorCode.CONTENT_INSPECTION_ALREADY_CONFIRMED);
        }

        Map<Long, ViolationStatus> requestedStatuses = requestedStatuses(request.violations());
        Set<Long> pendingIds = pendingItems.stream()
                .map(ViolationItem::getId)
                .collect(java.util.stream.Collectors.toSet());
        if (!pendingIds.equals(requestedStatuses.keySet())) {
            throw invalid("현재 PENDING 위반 항목 전체를 중복 없이 제출해야 합니다.");
        }
        validateDecision(request.decision(), requestedStatuses.values());

        for (ViolationItem item : pendingItems) {
            ViolationStatus target = requestedStatuses.get(item.getId());
            if (target == ViolationStatus.VIOLATION_CONFIRMED) {
                item.confirm();
                penaltyService.activateIfAbsent(
                        content.getSelectorsId(), contentVersionId,
                        item.getViolationTypeId(), violationReason(item), adminLoginId);
            } else {
                item.dismiss();
            }
        }
        version.confirmInspection(request.decision());
        violationItemRepository.flush();
        if (requestedStatuses.containsValue(ViolationStatus.VIOLATION_CONFIRMED)) {
            eventPublisher.publishEvent(new ContentViolationConfirmedEvent(
                    adminLoginId, contentId, content.getSelectorsId()));
        }
        return new ContentInspectionConfirmationResponse(pendingItems.size());
    }

    private Content requireContent(Long contentId) {
        return contentRepository.findById(contentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CONTENT_NOT_FOUND));
    }

    private ContentVersion requireOwnedVersionForUpdate(Long contentId, Long contentVersionId) {
        ContentVersion version = contentVersionRepository.findByIdForUpdate(contentVersionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CONTENT_VERSION_NOT_FOUND));
        if (!contentId.equals(version.getContentId())) {
            throw new BusinessException(ErrorCode.CONTENT_VERSION_NOT_FOUND);
        }
        return version;
    }

    private List<ViolationItem> latestReportItemsForUpdate(Long contentVersionId) {
        ContentReport report = contentReportRepository
                .findFirstByContentVersionIdOrderByIdDesc(contentVersionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_CONTENT_INSPECTION_STATUS));
        if (report.getInspectionPolicyId() == null) {
            return List.of();
        }
        List<Long> itemIds = historyRepository
                .findAllByContentVersionIdAndInspectionPolicyIdOrderByIdAsc(
                        contentVersionId, report.getInspectionPolicyId())
                .stream()
                .map(ViolationEvidenceHistory::getViolationItemId)
                .distinct()
                .toList();
        return itemIds.isEmpty() ? List.of()
                : violationItemRepository.findAllByIdInForUpdate(itemIds);
    }

    private void validateRequestStatuses(ContentInspectionConfirmationRequest request) {
        for (ViolationDecision violation : request.violations()) {
            if (violation == null || violation.violationItemId() == null
                    || violation.status() == null
                    || !ALLOWED_TARGET_STATUSES.contains(violation.status())) {
                throw invalid("위반 상태는 VIOLATION_CONFIRMED 또는 DISMISSED만 허용됩니다.");
            }
        }
    }

    private Map<Long, ViolationStatus> requestedStatuses(List<ViolationDecision> violations) {
        Map<Long, ViolationStatus> statuses = new HashMap<>();
        Set<Long> duplicated = new HashSet<>();
        for (ViolationDecision violation : violations) {
            if (statuses.put(violation.violationItemId(), violation.status()) != null) {
                duplicated.add(violation.violationItemId());
            }
        }
        if (!duplicated.isEmpty()) {
            throw invalid("중복된 violationItemId는 허용되지 않습니다.");
        }
        return statuses;
    }

    private void validateDecision(
            ContentInspectionDecision decision,
            java.util.Collection<ViolationStatus> statuses) {
        boolean hasConfirmed = statuses.contains(ViolationStatus.VIOLATION_CONFIRMED);
        if (decision == ContentInspectionDecision.APPROVED && hasConfirmed) {
            throw invalid("APPROVED는 모든 위반 항목이 DISMISSED여야 합니다.");
        }
        if (decision == ContentInspectionDecision.REJECTED && !hasConfirmed) {
            throw invalid("REJECTED는 VIOLATION_CONFIRMED 항목이 하나 이상 필요합니다.");
        }
    }

    private String violationReason(ViolationItem item) {
        if (item.getEvidence() == null || item.getEvidence().reason() == null
                || item.getEvidence().reason().isBlank()) {
            return "수정이 필요한 위반 사항";
        }
        return item.getEvidence().reason();
    }

    private BusinessException invalid(String message) {
        return new BusinessException(ErrorCode.INVALID_CONTENT_INSPECTION_CONFIRMATION, message);
    }
}
