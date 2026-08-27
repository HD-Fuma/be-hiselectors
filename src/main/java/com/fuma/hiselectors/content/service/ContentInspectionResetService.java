package com.fuma.hiselectors.content.service;

import com.fuma.hiselectors.content.dto.ContentInspectionResetResponse;
import com.fuma.hiselectors.content.model.ContentReport;
import com.fuma.hiselectors.content.model.ContentVersion;
import com.fuma.hiselectors.content.repository.ContentReportRepository;
import com.fuma.hiselectors.content.repository.ContentVersionRepository;
import com.fuma.hiselectors.exception.BusinessException;
import com.fuma.hiselectors.exception.ErrorCode;
import com.fuma.hiselectors.generation.service.GenerationService;
import com.fuma.hiselectors.inspection.model.ViolationEvidenceHistory;
import com.fuma.hiselectors.inspection.model.ViolationItem;
import com.fuma.hiselectors.inspection.repository.ViolationEvidenceHistoryRepository;
import com.fuma.hiselectors.inspection.repository.ViolationItemRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ContentInspectionResetService {

    public static final String CONFIRMATION = "RESET_CONTENT_INSPECTIONS";

    private final GenerationService generationService;
    private final ContentVersionRepository contentVersionRepository;
    private final ContentReportRepository contentReportRepository;
    private final ViolationEvidenceHistoryRepository historyRepository;
    private final ViolationItemRepository violationItemRepository;

    @Transactional
    public ContentInspectionResetResponse resetCurrentGeneration(String confirmation) {
        if (!CONFIRMATION.equals(confirmation)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "초기화 확인 문구가 일치하지 않습니다.");
        }

        List<ContentVersion> versions = contentVersionRepository
                .findConfirmedCurrentByGenerationIdForUpdate(
                        generationService.getCurrentActivity().getId());
        int resetViolationCount = 0;
        for (ContentVersion version : versions) {
            for (ViolationItem item : latestReportItemsForUpdate(version.getId())) {
                if (item.resetInspectionDecision()) {
                    resetViolationCount++;
                }
            }
            version.resetInspectionDecision();
        }
        return new ContentInspectionResetResponse(versions.size(), resetViolationCount);
    }

    private List<ViolationItem> latestReportItemsForUpdate(Long contentVersionId) {
        ContentReport report = contentReportRepository
                .findFirstByContentVersionIdOrderByIdDesc(contentVersionId)
                .orElse(null);
        if (report == null || report.getInspectionPolicyId() == null) {
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
}
