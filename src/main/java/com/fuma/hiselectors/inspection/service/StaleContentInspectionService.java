package com.fuma.hiselectors.inspection.service;

import com.fuma.hiselectors.content.repository.ContentVersionRepository;
import com.fuma.hiselectors.content.model.ContentVersionStatus;
import com.fuma.hiselectors.generation.service.GenerationService;
import com.fuma.hiselectors.inspection.dto.ReinspectStaleResponse;
import com.fuma.hiselectors.inspection.model.InspectionPolicy;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class StaleContentInspectionService {

    private static final int DEFAULT_LIMIT = 50;
    private static final int MAX_LIMIT = 200;

    private final InspectionPolicyService inspectionPolicyService;
    private final GenerationService generationService;
    private final ContentVersionRepository contentVersionRepository;
    private final ContentInspectionExecutionService contentInspectionExecutionService;

    public ReinspectStaleResponse reinspectStale(Integer limit) {
        int pageSize = normalizeLimit(limit);
        Long generationId = generationService.getActive().getId();
        List<Long> versionIds = new ArrayList<>();
        for (InspectionPolicy policy : inspectionPolicyService.requireAllActive()) {
            int remaining = pageSize - versionIds.size();
            if (remaining <= 0) {
                break;
            }
            versionIds.addAll(contentVersionRepository.findStaleLatestVersionIds(
                    generationId, policy.getPlatform(), policy.getId(),
                    ContentVersionStatus.INSPECTING, PageRequest.of(0, remaining)));
        }
        List<Long> failedVersionIds = new ArrayList<>();
        int successCount = 0;
        for (Long versionId : versionIds) {
            try {
                contentInspectionExecutionService.inspect(versionId);
                successCount++;
            } catch (RuntimeException e) {
                log.warn("최신 버전 재검수 실패: contentVersionId={}", versionId, e);
                failedVersionIds.add(versionId);
            }
        }
        return new ReinspectStaleResponse(
                versionIds.size(), successCount, failedVersionIds.size(), failedVersionIds);
    }

    private int normalizeLimit(Integer limit) {
        if (limit == null || limit < 1) {
            return DEFAULT_LIMIT;
        }
        return Math.min(limit, MAX_LIMIT);
    }
}
