package com.fuma.hiselectors.inspection.service;

import com.fuma.hiselectors.content.model.Content;
import com.fuma.hiselectors.content.model.ContentVersion;
import com.fuma.hiselectors.content.repository.ContentRepository;
import com.fuma.hiselectors.content.repository.ContentVersionRepository;
import com.fuma.hiselectors.exception.BusinessException;
import com.fuma.hiselectors.exception.ErrorCode;
import com.fuma.hiselectors.inspection.model.ViolationItem;
import com.fuma.hiselectors.inspection.model.ViolationStatus;
import com.fuma.hiselectors.inspection.repository.ViolationItemRepository;
import com.fuma.hiselectors.penalty.service.PenaltyService;
import com.fuma.hiselectors.selectors.model.Selectors;
import com.fuma.hiselectors.selectors.repository.SelectorsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ViolationConfirmationWriter {

    private final ViolationItemRepository violationItemRepository;
    private final ContentVersionRepository contentVersionRepository;
    private final ContentRepository contentRepository;
    private final SelectorsRepository selectorsRepository;
    private final PenaltyService penaltyService;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ConfirmationPreparation prepare(Long violationId) {
        ViolationItem item = requireViolationForUpdate(violationId);
        ContentVersion firstVersion = contentVersionRepository.findById(item.getContentVersionId())
                .orElseThrow(() -> new BusinessException(ErrorCode.CONTENT_VERSION_NOT_FOUND));
        Content content = contentRepository.findById(firstVersion.getContentId())
                .orElseThrow(() -> new BusinessException(ErrorCode.CONTENT_NOT_FOUND));
        Selectors selectors = selectorsRepository.findByIdForUpdate(content.getSelectorsId())
                .orElseThrow(() -> new BusinessException(ErrorCode.SELECTOR_NOT_FOUND));

        if (item.getStatus() == ViolationStatus.EDIT_REQUESTED) {
            return preparation(item, selectors, false, true);
        }
        if (item.getStatus() == ViolationStatus.PENDING) {
            item.confirm();
        } else if (item.getStatus() != ViolationStatus.VIOLATION_CONFIRMED) {
            throw new BusinessException(ErrorCode.INVALID_VIOLATION_STATUS_TRANSITION);
        }

        boolean penaltyCreated = penaltyService.activateIfAbsent(
                selectors.getId(), item.getViolationTypeId());
        return preparation(item, selectors, penaltyCreated, false);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ViolationStatus markEditRequested(Long violationId) {
        ViolationItem item = requireViolationForUpdate(violationId);
        if (item.getStatus() == ViolationStatus.VIOLATION_CONFIRMED) {
            item.requestEdit();
        } else if (item.getStatus() != ViolationStatus.EDIT_REQUESTED) {
            throw new BusinessException(ErrorCode.INVALID_VIOLATION_STATUS_TRANSITION);
        }
        return item.getStatus();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ViolationStatus dismiss(Long violationId) {
        ViolationItem item = requireViolationForUpdate(violationId);
        ContentVersion firstVersion = contentVersionRepository.findById(item.getContentVersionId())
                .orElseThrow(() -> new BusinessException(ErrorCode.CONTENT_VERSION_NOT_FOUND));
        Content content = contentRepository.findById(firstVersion.getContentId())
                .orElseThrow(() -> new BusinessException(ErrorCode.CONTENT_NOT_FOUND));
        selectorsRepository.findByIdForUpdate(content.getSelectorsId())
                .orElseThrow(() -> new BusinessException(ErrorCode.SELECTOR_NOT_FOUND));
        item.dismiss();
        violationItemRepository.flush();
        penaltyService.releaseIfEligible(content.getSelectorsId());
        return item.getStatus();
    }

    private ViolationItem requireViolationForUpdate(Long violationId) {
        return violationItemRepository.findByIdForUpdate(violationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.VIOLATION_NOT_FOUND));
    }

    private ConfirmationPreparation preparation(ViolationItem item, Selectors selectors,
                                                boolean penaltyCreated,
                                                boolean alreadyRequested) {
        String reason = item.getEvidence() == null ? "수정이 필요한 위반 사항"
                : item.getEvidence().reason();
        return new ConfirmationPreparation(
                item.getId(), selectors.getUserId(), selectors.getSelectorsNickname(), reason,
                penaltyCreated, alreadyRequested);
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
}
