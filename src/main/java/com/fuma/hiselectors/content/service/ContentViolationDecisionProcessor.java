package com.fuma.hiselectors.content.service;

import com.fuma.hiselectors.content.model.Content;
import com.fuma.hiselectors.content.model.ContentInspectionDecision;
import com.fuma.hiselectors.content.model.ContentVersion;
import com.fuma.hiselectors.exception.BusinessException;
import com.fuma.hiselectors.exception.ErrorCode;
import com.fuma.hiselectors.inspection.model.ViolationItem;
import com.fuma.hiselectors.inspection.model.ViolationStatus;
import com.fuma.hiselectors.penalty.service.PenaltyService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ContentViolationDecisionProcessor {

    private final PenaltyService penaltyService;

    public boolean confirm(
            Content content,
            ContentVersion version,
            ViolationItem item,
            String adminLoginId) {
        if (item.getStatus() == ViolationStatus.PENDING) {
            item.confirm();
        } else if (item.getStatus() != ViolationStatus.VIOLATION_CONFIRMED
                && item.getStatus() != ViolationStatus.EDIT_REQUESTED) {
            throw new BusinessException(ErrorCode.INVALID_VIOLATION_STATUS_TRANSITION);
        }

        if (version.getInspectionDecision() == null) {
            version.confirmInspection(ContentInspectionDecision.REJECTED);
        } else if (version.getInspectionDecision() != ContentInspectionDecision.REJECTED) {
            throw new BusinessException(ErrorCode.CONTENT_INSPECTION_ALREADY_CONFIRMED);
        }

        return penaltyService.activateIfAbsent(
                content.getSelectorsId(), version.getId(), item.getViolationTypeId(),
                violationReason(item), adminLoginId);
    }

    public void dismiss(ViolationItem item) {
        if (item.getStatus() == ViolationStatus.PENDING) {
            item.dismiss();
        } else if (item.getStatus() != ViolationStatus.DISMISSED) {
            throw new BusinessException(ErrorCode.INVALID_VIOLATION_STATUS_TRANSITION);
        }
    }

    public void approve(ContentVersion version) {
        if (version.getInspectionDecision() == null) {
            version.confirmInspection(ContentInspectionDecision.APPROVED);
        } else if (version.getInspectionDecision() != ContentInspectionDecision.APPROVED) {
            throw new BusinessException(ErrorCode.CONTENT_INSPECTION_ALREADY_CONFIRMED);
        }
    }

    public void resolveCorrection(ViolationItem item, ContentVersion version) {
        item.confirmCorrection(version);
    }

    private String violationReason(ViolationItem item) {
        if (item.getEvidence() == null || item.getEvidence().reason() == null
                || item.getEvidence().reason().isBlank()) {
            return "수정이 필요한 위반 사항";
        }
        return item.getEvidence().reason();
    }
}
