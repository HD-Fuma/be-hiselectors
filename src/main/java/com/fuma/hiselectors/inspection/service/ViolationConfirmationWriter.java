package com.fuma.hiselectors.inspection.service;

import com.fuma.hiselectors.content.model.Content;
import com.fuma.hiselectors.content.model.ContentVersion;
import com.fuma.hiselectors.content.repository.ContentRepository;
import com.fuma.hiselectors.content.repository.ContentVersionRepository;
import com.fuma.hiselectors.content.service.ContentViolationDecisionProcessor;
import com.fuma.hiselectors.exception.BusinessException;
import com.fuma.hiselectors.exception.ErrorCode;
import com.fuma.hiselectors.inspection.model.ViolationItem;
import com.fuma.hiselectors.inspection.model.ViolationStatus;
import com.fuma.hiselectors.inspection.repository.ViolationItemRepository;
import com.fuma.hiselectors.penalty.service.PenaltyService;
import com.fuma.hiselectors.selectors.model.Selectors;
import com.fuma.hiselectors.selectors.repository.SelectorsRepository;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ViolationConfirmationWriter {

    private static final Set<ViolationStatus> OPEN_STATUSES = EnumSet.of(
            ViolationStatus.PENDING,
            ViolationStatus.VIOLATION_CONFIRMED,
            ViolationStatus.EDIT_REQUESTED,
            ViolationStatus.CORRECTION_REVIEW_PENDING);

    private final ViolationItemRepository violationItemRepository;
    private final ContentRepository contentRepository;
    private final ContentVersionRepository contentVersionRepository;
    private final SelectorsRepository selectorsRepository;
    private final ContentViolationDecisionProcessor decisionProcessor;
    private final PenaltyService penaltyService;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ConfirmationPreparation prepare(Long violationId, String adminLoginId) {
        ViolationItem item = requireViolationForUpdate(violationId);
        ConfirmationContext context = requireCurrentContext(item);
        Content content = context.content();
        Selectors selectors = selectorsRepository.findByIdForUpdate(content.getSelectorsId())
                .orElseThrow(() -> new BusinessException(ErrorCode.SELECTOR_NOT_FOUND));

        if (item.getStatus() == ViolationStatus.EDIT_REQUESTED) {
            return preparation(item, selectors, false, true);
        }
        boolean penaltyCreated = decisionProcessor.confirm(
                content, context.version(), item, adminLoginId);
        return preparation(item, selectors, penaltyCreated, false);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ViolationStatus markEditRequested(Long violationId) {
        markEditRequested(List.of(violationId));
        return ViolationStatus.EDIT_REQUESTED;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markEditRequested(Collection<Long> violationIds) {
        List<ViolationItem> items = violationItemRepository
                .findAllByIdInForUpdate(violationIds);
        if (items.size() != Set.copyOf(violationIds).size()) {
            throw new BusinessException(ErrorCode.VIOLATION_NOT_FOUND);
        }
        for (ViolationItem item : items) {
            if (item.getStatus() == ViolationStatus.VIOLATION_CONFIRMED) {
                item.requestEdit();
            } else if (item.getStatus() != ViolationStatus.EDIT_REQUESTED) {
                throw new BusinessException(ErrorCode.INVALID_VIOLATION_STATUS_TRANSITION);
            }
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ViolationStatus dismiss(Long violationId) {
        ViolationItem item = requireViolationForUpdate(violationId);
        ConfirmationContext context = requireCurrentContext(item);
        Content content = context.content();
        selectorsRepository.findByIdForUpdate(content.getSelectorsId())
                .orElseThrow(() -> new BusinessException(ErrorCode.SELECTOR_NOT_FOUND));
        decisionProcessor.dismiss(item);
        violationItemRepository.flush();
        if (context.version().getInspectionDecision() == null
                && violationItemRepository
                        .findAllByContentIdAndStatusInOrderByIdAsc(
                                content.getId(), OPEN_STATUSES)
                        .isEmpty()) {
            decisionProcessor.approve(context.version());
        }
        penaltyService.releaseIfEligible(content.getSelectorsId());
        return item.getStatus();
    }

    private ViolationItem requireViolationForUpdate(Long violationId) {
        return violationItemRepository.findByIdForUpdate(violationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.VIOLATION_NOT_FOUND));
    }

    private ConfirmationContext requireCurrentContext(ViolationItem item) {
        Content content = contentRepository.findByIdForUpdate(item.getContentId())
                .orElseThrow(() -> new BusinessException(ErrorCode.CONTENT_NOT_FOUND));
        ContentVersion version = contentVersionRepository
                .findByIdForUpdate(item.getLastDetectedContentVersionId())
                .filter(candidate -> candidate.getContentId().equals(content.getId()))
                .filter(candidate -> candidate.getVersionNo().equals(content.getLastVersionNo()))
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.HISTORICAL_CONTENT_VERSION_INSPECTION_NOT_ALLOWED));
        return new ConfirmationContext(content, version);
    }

    private ConfirmationPreparation preparation(ViolationItem item, Selectors selectors,
                                                boolean penaltyCreated,
                                                boolean alreadyRequested) {
        String reason = violationReason(item);
        return new ConfirmationPreparation(
                item.getId(), selectors.getUserId(), selectors.getSelectorsNickname(), reason,
                penaltyCreated, alreadyRequested);
    }

    private String violationReason(ViolationItem item) {
        if (item.getEvidence() == null || item.getEvidence().reason() == null
                || item.getEvidence().reason().isBlank()) {
            return "수정이 필요한 위반 사항";
        }
        return item.getEvidence().reason();
    }

    public record ConfirmationPreparation(
            Long violationId,
            Long recipientUserId,
            String receiverName,
            String reason,
            boolean penaltyCreated,
            boolean alreadyRequested
    ) {
    }

    private record ConfirmationContext(Content content, ContentVersion version) {
    }
}
