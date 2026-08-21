package com.fuma.hiselectors.inspection.service;

import com.fuma.hiselectors.content.model.ContentVersion;
import com.fuma.hiselectors.inspection.model.ViolationEvidence;
import com.fuma.hiselectors.inspection.model.ViolationEvidenceHistory;
import com.fuma.hiselectors.inspection.model.ViolationItem;
import com.fuma.hiselectors.inspection.repository.ViolationEvidenceHistoryRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ViolationEvidenceHistoryService {

    private final ViolationEvidenceHistoryRepository historyRepository;
    private final Clock clock;

    public void upsert(ViolationItem item, ContentVersion version, Long inspectionPolicyId) {
        ViolationEvidence evidence = item.getEvidence();
        LocalDateTime detectedAt = LocalDateTime.now(clock);
        historyRepository.findByViolationItemIdAndContentVersionIdAndInspectionPolicyId(
                        item.getId(), version.getId(), inspectionPolicyId)
                .ifPresentOrElse(
                        history -> history.overwrite(evidence, detectedAt),
                        () -> historyRepository.save(ViolationEvidenceHistory.create(
                                item.getId(), version.getId(), inspectionPolicyId,
                                evidence, detectedAt)));
    }
}
