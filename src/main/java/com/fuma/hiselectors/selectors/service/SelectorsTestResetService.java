package com.fuma.hiselectors.selectors.service;

import com.fuma.hiselectors.application.model.SnsPlatform;
import com.fuma.hiselectors.exception.BusinessException;
import com.fuma.hiselectors.exception.ErrorCode;
import com.fuma.hiselectors.logging.BatchEventLogger;
import com.fuma.hiselectors.logging.BatchLogContext;
import com.fuma.hiselectors.selectors.dto.SelectorsTestResetResponse;
import com.fuma.hiselectors.selectors.repository.SelectorsTestResetRepository;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 테스트 계정을 지원 이전 상태로 되돌린다.
 *
 * <p>플랫폼과 SNS 계정명으로 지원서·셀렉터스를 찾아 거기 매달린 행을 전부 물리 삭제한다.
 * 로그인 계정({@code users})은 남겨 두므로 같은 HiID 로 곧바로 다시 지원할 수 있다.
 *
 * <p>되돌릴 수 없는 작업이라 운영 데이터에 쓰면 안 된다. 삭제 결과는
 * {@code BATCH_EVENT} 로그에 관리자 로그인 ID 와 함께 남는다.
 */
@Service
@RequiredArgsConstructor
public class SelectorsTestResetService {

    private static final String BATCH_NAME = "selectors-test-reset";
    private static final int MAX_LOGGED_CODES_LENGTH = 200;

    private final SelectorsTestResetRepository resetRepository;
    private final BatchEventLogger batchEventLogger;

    @Transactional
    public SelectorsTestResetResponse reset(
            SnsPlatform snsCode, String accountId, String adminLoginId) {
        String normalized = normalize(accountId);
        Set<String> candidates = Set.of(normalized, "@" + normalized);

        Set<Long> applicationIds = new LinkedHashSet<>(
                resetRepository.findApplicationIds(snsCode, candidates));
        Set<Long> selectorsIds = new LinkedHashSet<>(
                resetRepository.findSelectorsIdsBySnsAccount(snsCode, candidates));

        // 지원서와 셀렉터스는 서로를 참조한다. 계정명이 한쪽에서만 걸리는 경우가 있어 서로를 채운다.
        if (!applicationIds.isEmpty()) {
            selectorsIds.addAll(resetRepository.findSelectorsIdsByApplicationIds(applicationIds));
        }
        if (!selectorsIds.isEmpty()) {
            applicationIds.addAll(resetRepository.findApplicationIdsBySelectorsIds(selectorsIds));
        }

        if (applicationIds.isEmpty() && selectorsIds.isEmpty()) {
            throw new BusinessException(ErrorCode.SELECTOR_NOT_FOUND,
                    "%s 플랫폼에서 '%s' 계정의 지원 이력이나 셀렉터스를 찾지 못했습니다."
                            .formatted(snsCode, accountId));
        }

        String selectorsCodes = String.join(",", resetRepository.findSelectorsCodes(selectorsIds));
        BatchLogContext logContext = batchEventLogger.start(BATCH_NAME);
        Map<String, Integer> deletedRowCounts;
        try {
            deletedRowCounts = deleteAll(selectorsIds, applicationIds);
        } catch (RuntimeException | Error error) {
            batchEventLogger.failed(logContext, error);
            throw error;
        }

        int deletedRowCount = deletedRowCounts.values().stream().mapToInt(Integer::intValue).sum();
        batchEventLogger.succeeded(logContext,
                Map.of("deletedRowCount", (long) deletedRowCount,
                        "selectorsCount", (long) selectorsIds.size(),
                        "applicationCount", (long) applicationIds.size()),
                Map.of("adminLoginId", adminLoginId == null ? "unknown" : adminLoginId,
                        "snsCode", snsCode.name(),
                        "accountId", normalized,
                        "selectorsCodes", bounded(selectorsCodes)));

        return new SelectorsTestResetResponse(
                snsCode,
                normalized,
                List.copyOf(selectorsIds),
                List.copyOf(applicationIds),
                deletedRowCount,
                deletedRowCounts);
    }

    /**
     * 외래키를 거스르지 않도록 자식 테이블부터 지운다. 호출 순서가 곧 정확성이다.
     *
     * <p>{@code penalty_history} 는 {@code content_version} 을, {@code selectors} 는
     * {@code application} 을 참조하므로 각각 부모보다 먼저 지운다.
     */
    private Map<String, Integer> deleteAll(
            Collection<Long> selectorsIds, Collection<Long> applicationIds) {
        List<Long> contentIds = selectorsIds.isEmpty()
                ? List.of() : resetRepository.findContentIds(selectorsIds);
        List<Long> contentVersionIds = contentIds.isEmpty()
                ? List.of() : resetRepository.findContentVersionIds(contentIds);

        Map<String, Integer> counts = new LinkedHashMap<>();
        record(counts, "violation_evidence_history",
                resetRepository.deleteViolationEvidenceHistories(contentVersionIds));
        record(counts, "violation_item", resetRepository.deleteViolationItems(contentIds));
        record(counts, "penalty_history", resetRepository.deletePenaltyHistories(selectorsIds));
        record(counts, "content_report", resetRepository.deleteContentReports(contentVersionIds));
        record(counts, "content_media", resetRepository.deleteContentMedia(contentVersionIds));
        record(counts, "content_engagement", resetRepository.deleteContentEngagements(contentIds));
        record(counts, "content_version", resetRepository.deleteContentVersions(contentIds));
        record(counts, "content", resetRepository.deleteContents(contentIds));
        record(counts, "click_log", resetRepository.deleteClickLogs(selectorsIds));
        record(counts, "purchase_history", resetRepository.deletePurchaseHistories(selectorsIds));
        record(counts, "product_group_item",
                resetRepository.deleteProductGroupItems(selectorsIds));
        record(counts, "product_group", resetRepository.deleteProductGroups(selectorsIds));
        record(counts, "blacklist_history",
                resetRepository.deleteBlacklistHistories(selectorsIds));
        record(counts, "settlement_history",
                resetRepository.deleteSettlementHistories(selectorsIds));
        record(counts, "settlement_account",
                resetRepository.deleteSettlementAccounts(selectorsIds));
        record(counts, "selector_excellence_selection",
                resetRepository.deleteExcellenceSelections(selectorsIds));
        record(counts, "selectors_generation",
                resetRepository.deleteSelectorsGenerations(selectorsIds));
        record(counts, "selectors_sns_account",
                resetRepository.deleteSelectorsSnsAccounts(selectorsIds));
        record(counts, "selectors", resetRepository.deleteSelectors(selectorsIds));
        record(counts, "application_media",
                resetRepository.deleteApplicationMedia(applicationIds));
        record(counts, "application_report",
                resetRepository.deleteApplicationReports(applicationIds));
        record(counts, "application_content_analysis",
                resetRepository.deleteApplicationContentAnalyses(applicationIds));
        record(counts, "application", resetRepository.deleteApplications(applicationIds));
        return counts;
    }

    private void record(Map<String, Integer> counts, String table, int deleted) {
        if (deleted > 0) {
            counts.put(table, deleted);
        }
    }

    /** 관리자가 {@code @handle} 로 붙여넣는 경우가 잦아 앞의 {@code @} 를 떼고 소문자로 맞춘다. */
    private String normalize(String accountId) {
        if (accountId == null || accountId.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "SNS 계정명을 입력해주세요.");
        }
        String trimmed = accountId.trim();
        String stripped = trimmed.startsWith("@") ? trimmed.substring(1) : trimmed;
        if (stripped.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "SNS 계정명을 입력해주세요.");
        }
        return stripped.toLowerCase();
    }

    private String bounded(String value) {
        return value.length() <= MAX_LOGGED_CODES_LENGTH
                ? value : value.substring(0, MAX_LOGGED_CODES_LENGTH);
    }
}
