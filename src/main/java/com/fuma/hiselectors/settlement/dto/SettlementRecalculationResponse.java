package com.fuma.hiselectors.settlement.dto;

import java.time.YearMonth;

/**
 * 관리자 정산 정합성 보정 결과. 각 카운트는 셀렉터스·활동월 한 건의 처리 결과를 집계한다.
 * created=신규 생성, updated=CALCULATING 재계산, finalized=PAYMENT_PENDING 확정,
 * skipped=계산 완료 상태라 보존, failed=처리 오류.
 */
public record SettlementRecalculationResponse(
        Long selectorsId,
        YearMonth requestedActivityMonth,
        YearMonth startActivityMonth,
        YearMonth endActivityMonth,
        int selectorsCount,
        int activityMonthsCount,
        int createdCount,
        int updatedCount,
        int finalizedCount,
        int skippedCount,
        int failedCount
) {
}
