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
            ViolationStatus.DISMISSED,
            ViolationStatus.RESOLVED);

    private final ContentRepository contentRepository;
    private final ContentVersionRepository contentVersionRepository;
    private final ContentReportRepository contentReportRepository;
    private final ViolationEvidenceHistoryRepository historyRepository;
    private final ViolationItemRepository violationItemRepository;
    private final ContentViolationDecisionProcessor decisionProcessor;
    private final PenaltyService penaltyService;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public ContentInspectionConfirmationResponse confirm(
            Long contentId,
            Long contentVersionId,
            ContentInspectionConfirmationRequest request,
            String adminLoginId) {
        Content content = requireContent(contentId);
        ContentVersion version = requireCurrentVersionForUpdate(
                contentId, content, contentVersionId);
        if (version.getInspectionDecision() != null
                && version.getInspectionDecision() != request.decision()) {
            throw new BusinessException(ErrorCode.CONTENT_INSPECTION_ALREADY_CONFIRMED);
        }

        validateRequestStatuses(request);
        List<ViolationItem> versionItems = latestReportItemsForUpdate(contentVersionId);
        List<ViolationItem> actionableItems = versionItems.stream()
                .filter(item -> item.getStatus() == ViolationStatus.PENDING
                        || item.getStatus() == ViolationStatus.CORRECTION_REVIEW_PENDING)
                .toList();

        if (version.getInspectionDecision() != null && actionableItems.isEmpty()) {
            throw new BusinessException(ErrorCode.CONTENT_INSPECTION_ALREADY_CONFIRMED);
        }
        if (!versionItems.isEmpty() && actionableItems.isEmpty()) {
            throw new BusinessException(ErrorCode.CONTENT_INSPECTION_ALREADY_CONFIRMED);
        }

        Map<Long, ViolationStatus> requestedStatuses = requestedStatuses(request.violations());
        Set<Long> actionableIds = actionableItems.stream()
                .map(ViolationItem::getId)
                .collect(java.util.stream.Collectors.toSet());
        if (!actionableIds.equals(requestedStatuses.keySet())) {
            throw invalid("현재 검수 대상 위반 항목 전체를 중복 없이 제출해야 합니다.");
        }
        validateTransitions(actionableItems, requestedStatuses);
        boolean previouslyConfirmed = version.getInspectionDecision()
                == ContentInspectionDecision.REJECTED
                || versionItems.stream().anyMatch(item ->
                        item.getStatus() == ViolationStatus.VIOLATION_CONFIRMED
                                || item.getStatus() == ViolationStatus.EDIT_REQUESTED);
        validateDecision(
                request.decision(), requestedStatuses.values(), previouslyConfirmed);

        for (ViolationItem item : actionableItems) {
            ViolationStatus target = requestedStatuses.get(item.getId());
            if (item.getStatus() == ViolationStatus.CORRECTION_REVIEW_PENDING) {
                decisionProcessor.resolveCorrection(item, version);
            } else if (target == ViolationStatus.VIOLATION_CONFIRMED) {
                decisionProcessor.confirm(content, version, item, adminLoginId);
            } else {
                decisionProcessor.dismiss(item);
            }
        }
        if (request.decision() == ContentInspectionDecision.APPROVED) {
            decisionProcessor.approve(version);
        }
        violationItemRepository.flush();
        penaltyService.releaseIfEligible(content.getSelectorsId());
        List<Long> confirmedIds = actionableItems.stream()
                .filter(item -> item.getStatus() == ViolationStatus.VIOLATION_CONFIRMED)
                .map(ViolationItem::getId)
                .toList();
        if (!confirmedIds.isEmpty()) {
            eventPublisher.publishEvent(new ContentViolationConfirmedEvent(
                    adminLoginId, contentId, content.getSelectorsId(), confirmedIds));
        }
        return new ContentInspectionConfirmationResponse(actionableItems.size());
    }

    private Content requireContent(Long contentId) {
        return contentRepository.findByIdForUpdate(contentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CONTENT_NOT_FOUND));
    }

    private ContentVersion requireCurrentVersionForUpdate(
            Long requestedContentId, Content content, Long contentVersionId) {
        ContentVersion version = contentVersionRepository.findByIdForUpdate(contentVersionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CONTENT_VERSION_NOT_FOUND));
        if (!requestedContentId.equals(content.getId())
                || !content.getId().equals(version.getContentId())
                || !content.getLastVersionNo().equals(version.getVersionNo())) {
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
        Map<Long, ViolationItem> itemsById = new java.util.LinkedHashMap<>();
        if (!itemIds.isEmpty()) {
            violationItemRepository.findAllByIdInForUpdate(itemIds)
                    .forEach(item -> itemsById.put(item.getId(), item));
        }
        violationItemRepository.findAllByResolutionCandidateForUpdate(
                        contentVersionId, ViolationStatus.CORRECTION_REVIEW_PENDING)
                .forEach(item -> itemsById.putIfAbsent(item.getId(), item));
        return List.copyOf(itemsById.values());
    }

    private void validateRequestStatuses(ContentInspectionConfirmationRequest request) {
        for (ViolationDecision violation : request.violations()) {
            if (violation == null || violation.violationItemId() == null
                    || violation.status() == null
                    || !ALLOWED_TARGET_STATUSES.contains(violation.status())) {
                throw invalid("허용되지 않은 위반 상태입니다.");
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

    private void validateTransitions(
            List<ViolationItem> items, Map<Long, ViolationStatus> requestedStatuses) {
        for (ViolationItem item : items) {
            ViolationStatus target = requestedStatuses.get(item.getId());
            boolean valid = item.getStatus() == ViolationStatus.PENDING
                    ? target == ViolationStatus.VIOLATION_CONFIRMED
                            || target == ViolationStatus.DISMISSED
                    : item.getStatus() == ViolationStatus.CORRECTION_REVIEW_PENDING
                            && target == ViolationStatus.RESOLVED;
            if (!valid) {
                throw invalid("현재 위반 상태에서 요청한 상태로 변경할 수 없습니다.");
            }
        }
    }

    private void validateDecision(
            ContentInspectionDecision decision,
            java.util.Collection<ViolationStatus> statuses,
            boolean previouslyConfirmed) {
        boolean hasConfirmed = previouslyConfirmed
                || statuses.contains(ViolationStatus.VIOLATION_CONFIRMED);
        if (decision == ContentInspectionDecision.APPROVED && hasConfirmed) {
            throw invalid("APPROVED는 모든 위반 항목이 DISMISSED여야 합니다.");
        }
        if (decision == ContentInspectionDecision.REJECTED && !hasConfirmed) {
            throw invalid("REJECTED는 VIOLATION_CONFIRMED 항목이 하나 이상 필요합니다.");
        }
    }

    private BusinessException invalid(String message) {
        return new BusinessException(ErrorCode.INVALID_CONTENT_INSPECTION_CONFIRMATION, message);
    }
}
