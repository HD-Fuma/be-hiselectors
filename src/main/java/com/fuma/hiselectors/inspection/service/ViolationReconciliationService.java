package com.fuma.hiselectors.inspection.service;

import com.fuma.hiselectors.content.model.Content;
import com.fuma.hiselectors.content.model.ContentVersion;
import com.fuma.hiselectors.exception.BusinessException;
import com.fuma.hiselectors.exception.ErrorCode;
import com.fuma.hiselectors.inspection.model.DetectedViolation;
import com.fuma.hiselectors.inspection.model.ViolationItem;
import com.fuma.hiselectors.inspection.model.ViolationStatus;
import com.fuma.hiselectors.inspection.model.ViolationType;
import com.fuma.hiselectors.inspection.model.ViolationTypeCode;
import com.fuma.hiselectors.inspection.repository.ViolationItemRepository;
import com.fuma.hiselectors.inspection.repository.ViolationTypeRepository;
import com.fuma.hiselectors.penalty.service.PenaltyService;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ViolationReconciliationService {

    private final ViolationItemRepository violationItemRepository;
    private final ViolationTypeRepository violationTypeRepository;
    private final ViolationEvidenceHistoryService historyService;
    private final PenaltyService penaltyService;

    @Transactional
    public boolean reconcile(Content content, ContentVersion newVersion,
                             List<DetectedViolation> detectedViolations,
                             Long inspectionPolicyId) {
        Map<ViolationTypeCode, DetectedViolation> detectedByType = detectedViolations.stream()
                .collect(Collectors.toMap(DetectedViolation::type, Function.identity()));
        List<ViolationItem> existingItems = violationItemRepository
                .findAllByContentIdForUpdate(content.getId());

        Map<Long, ViolationTypeCode> codeByTypeId = violationTypeRepository
                .findAllById(existingItems.stream().map(ViolationItem::getViolationTypeId).toList())
                .stream().collect(Collectors.toMap(ViolationType::getId, ViolationType::getCode));

        for (ViolationItem existing : existingItems) {
            ViolationTypeCode code = codeByTypeId.get(existing.getViolationTypeId());
            if (code == null) {
                throw new BusinessException(ErrorCode.VIOLATION_TYPE_NOT_FOUND);
            }
            DetectedViolation current = detectedByType.remove(code);
            if (current == null) {
                if (existing.getStatus() == ViolationStatus.VIOLATION_CONFIRMED
                        || existing.getStatus() == ViolationStatus.EDIT_REQUESTED
                        || existing.getStatus() == ViolationStatus.CORRECTION_REVIEW_PENDING) {
                    existing.awaitCorrectionReview(newVersion);
                } else if (existing.isOpen()) {
                    existing.resolve(newVersion);
                }
                continue;
            }
            if (existing.isOpen()) {
                existing.redetectForReview(newVersion, current.evidence());
            } else {
                existing.reopen(newVersion, current.evidence());
            }
            historyService.upsert(existing, newVersion, inspectionPolicyId);
        }

        if (!detectedByType.isEmpty()) {
            Map<ViolationTypeCode, ViolationType> typeByCode = new HashMap<>();
            violationTypeRepository.findAllByCodeIn(detectedByType.keySet())
                    .forEach(type -> typeByCode.put(type.getCode(), type));
            for (DetectedViolation detected : detectedByType.values()) {
                ViolationType type = typeByCode.get(detected.type());
                if (type == null) {
                    throw new BusinessException(ErrorCode.VIOLATION_TYPE_NOT_FOUND,
                            "등록되지 않은 위반 유형입니다: " + detected.type());
                }
                ViolationItem created = violationItemRepository.save(ViolationItem.pending(
                        newVersion, type.getId(), detected.evidence()));
                historyService.upsert(created, newVersion, inspectionPolicyId);
            }
        }

        violationItemRepository.flush();
        penaltyService.releaseIfEligible(content.getSelectorsId());
        return existingItems.stream().anyMatch(item ->
                item.getStatus() == ViolationStatus.CORRECTION_REVIEW_PENDING);
    }
}
