package com.fuma.hiselectors.settlement.dto;

import java.util.List;

/** 로그인한 셀렉터스의 특정 연도 정산 이력과 선택 가능한 연도 목록. */
public record SettlementHistoryListResponse(
        int selectedYear,
        List<Integer> availableYears,
        List<SettlementEstimateResponse> histories
) {
}
