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
import java.util.EnumSet;
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

    private static final EnumSet<ViolationStatus> OPEN_STATUSES = EnumSet.of(
            ViolationStatus.PENDING,
            ViolationStatus.VIOLATION_CONFIRMED,
            ViolationStatus.EDIT_REQUESTED);

    private final ViolationItemRepository violationItemRepository;
    private final ViolationTypeRepository violationTypeRepository;
    private final PenaltyService penaltyService;

    @Transactional
    public void reconcile(Content content, ContentVersion newVersion,
                          List<DetectedViolation> detectedViolations) {
        Map<ViolationTypeCode, DetectedViolation> detectedByType = detectedViolations.stream()
                .collect(Collectors.toMap(DetectedViolation::type, Function.identity()));
        List<ViolationItem> openItems = violationItemRepository
                .findOpenByContentIdForUpdate(content.getId(), OPEN_STATUSES);

        Map<Long, ViolationTypeCode> codeByTypeId = violationTypeRepository
                .findAllById(openItems.stream().map(ViolationItem::getViolationTypeId).toList())
                .stream().collect(Collectors.toMap(ViolationType::getId, ViolationType::getCode));

        for (ViolationItem existing : openItems) {
            ViolationTypeCode code = codeByTypeId.get(existing.getViolationTypeId());
            if (code == null) {
                throw new BusinessException(ErrorCode.VIOLATION_TYPE_NOT_FOUND);
            }
            DetectedViolation current = detectedByType.remove(code);
            if (current == null) {
                existing.resolve(newVersion);
            } else {
                existing.detectAgain(newVersion, current.evidence());
            }
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
                violationItemRepository.save(ViolationItem.pending(
                        newVersion, type.getId(), detected.evidence()));
            }
        }

        violationItemRepository.flush();
        penaltyService.releaseIfEligible(content.getSelectorsId());
    }
}
