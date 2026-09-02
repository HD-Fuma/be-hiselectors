package com.fuma.hiselectors.inspection.service;

import com.fuma.hiselectors.application.model.SnsPlatform;
import com.fuma.hiselectors.content.model.ContentVersionStatus;
import com.fuma.hiselectors.content.repository.ContentVersionRepository;
import com.fuma.hiselectors.exception.BusinessException;
import com.fuma.hiselectors.exception.ErrorCode;
import com.fuma.hiselectors.generation.service.GenerationService;
import com.fuma.hiselectors.inspection.dto.ReinspectStaleResponse;
import com.fuma.hiselectors.inspection.model.InspectionPolicy;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class StaleContentInspectionService {

    private final InspectionPolicyService inspectionPolicyService;
    private final GenerationService generationService;
    private final ContentVersionRepository contentVersionRepository;
    private final ContentInspectionExecutionService contentInspectionExecutionService;

    public ReinspectStaleResponse reinspectStale(Integer limit) {
        return reinspectStale(
                limit,
                Set.of(),
                contentInspectionExecutionService::inspect,
                ignored -> {
                });
    }

    public ReinspectStaleResponse reinspectStale(
            Integer limit,
            Set<Long> excludedVersionIds,
            Consumer<Long> inspector,
            Consumer<ReinspectStaleResponse> progressCallback) {
        return reinspectStale(
                limit, Map.of(), excludedVersionIds, inspector, progressCallback);
    }

    public ReinspectStaleResponse reinspectStale(
            Integer limit,
            Map<SnsPlatform, String> targetAccountIds,
            Set<Long> excludedVersionIds,
            Consumer<Long> inspector,
            Consumer<ReinspectStaleResponse> progressCallback) {
        Objects.requireNonNull(targetAccountIds, "대상 SNS 계정 목록은 필수입니다.");
        Objects.requireNonNull(excludedVersionIds, "배제 버전 ID 목록은 필수입니다.");
        Objects.requireNonNull(inspector, "검수 실행 함수는 필수입니다.");
        Objects.requireNonNull(progressCallback, "진행 callback은 필수입니다.");
        Set<Long> exclusions = Set.copyOf(excludedVersionIds);
        List<Long> versionIds = selectStaleLatestVersionIds(
                limit, Map.copyOf(targetAccountIds), exclusions);
        List<Long> failedVersionIds = new ArrayList<>();
        int successCount = 0;
        ReinspectStaleResponse snapshot = snapshot(versionIds.size(), successCount, failedVersionIds);
        progressCallback.accept(snapshot);
        for (Long versionId : versionIds) {
            try {
                inspector.accept(versionId);
                successCount++;
            } catch (RuntimeException e) {
                if (e instanceof BusinessException businessException
                        && (businessException.getErrorCode() == ErrorCode.TASK_RUN_LEASE_LOST
                        || businessException.getErrorCode()
                        == ErrorCode.AI_CONTENT_INSPECTION_QUOTA_EXCEEDED)) {
                    throw businessException;
                }
                log.warn("최신 버전 재검수 실패: contentVersionId={}", versionId, e);
                failedVersionIds.add(versionId);
            }
            snapshot = snapshot(versionIds.size(), successCount, failedVersionIds);
            progressCallback.accept(snapshot);
        }
        return snapshot;
    }

    public boolean hasStaleLatestVersions(Set<Long> excludedVersionIds) {
        Objects.requireNonNull(excludedVersionIds, "배제 버전 ID 목록은 필수입니다.");
        Set<Long> exclusions = Set.copyOf(excludedVersionIds);
        return !selectStaleLatestVersionIds(1, Map.of(), exclusions).isEmpty();
    }

    private List<Long> selectStaleLatestVersionIds(
            Integer limit,
            Map<SnsPlatform, String> targetAccountIds,
            Set<Long> exclusions) {
        Long generationId = generationService.getCurrentActivity().getId();
        List<Long> versionIds = new ArrayList<>();
        for (InspectionPolicy policy : inspectionPolicyService.requireAllActive()) {
            if (limit != null && versionIds.size() >= limit) {
                break;
            }
            String targetAccountId = targetAccountIds.get(policy.getPlatform());
            if (!targetAccountIds.isEmpty() && targetAccountId == null) {
                continue;
            }
            Pageable pageable = limit == null
                    ? Pageable.unpaged()
                    : PageRequest.of(0, Math.max(1, limit - versionIds.size()) + exclusions.size());
            List<Long> candidates = contentVersionRepository.findStaleLatestVersionIds(
                    generationId,
                    policy.getPlatform(),
                    policy.getId(),
                    ContentVersionStatus.INSPECTING,
                    targetAccountId,
                    pageable);
            Stream<Long> selected = candidates.stream()
                    .filter(versionId -> !exclusions.contains(versionId));
            if (limit != null) {
                selected = selected.limit(limit - (long) versionIds.size());
            }
            selected.forEach(versionIds::add);
        }
        return versionIds;
    }

    private ReinspectStaleResponse snapshot(
            int targetCount, int successCount, List<Long> failedVersionIds) {
        return new ReinspectStaleResponse(
                targetCount,
                successCount,
                failedVersionIds.size(),
                List.copyOf(failedVersionIds));
    }
}
