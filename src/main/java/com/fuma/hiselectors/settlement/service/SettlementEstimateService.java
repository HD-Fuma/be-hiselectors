package com.fuma.hiselectors.settlement.service;

import com.fuma.hiselectors.exception.BusinessException;
import com.fuma.hiselectors.exception.ErrorCode;
import com.fuma.hiselectors.selectors.model.Selectors;
import com.fuma.hiselectors.selectors.service.SelectorAccessService;
import com.fuma.hiselectors.settlement.dto.SettlementEstimateResponse;
import com.fuma.hiselectors.settlement.dto.SettlementHistoryListResponse;
import com.fuma.hiselectors.settlement.model.SettlementHistory;
import com.fuma.hiselectors.settlement.repository.SettlementHistoryRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SettlementEstimateService {

    private final SettlementHistoryRepository settlementHistoryRepository;
    private final Clock clock;
    private final SelectorAccessService selectorAccessService;

    public SettlementEstimateResponse getEstimate(String loginId, YearMonth requestedMonth) {
        Selectors selectors = selectorAccessService.requireCurrent(loginId);
        YearMonth activityMonth = resolveReadableMonth(requestedMonth);
        SettlementHistory history = settlementHistoryRepository
                .findBySelectorsIdAndActivityMonth(
                        selectors.getId(), activityMonth.atDay(1).atStartOfDay())
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.SETTLEMENT_NOT_CALCULATED));
        return SettlementEstimateResponse.of(history, selectors);
    }

    public SettlementHistoryListResponse getHistories(String loginId, Integer requestedYear) {
        Selectors selectors = selectorAccessService.requireSettlementHistoryReadable(loginId);
        int selectedYear = requestedYear == null ? LocalDate.now(clock).getYear() : requestedYear;
        LocalDateTime startMonth = YearMonth.of(selectedYear, 1).atDay(1).atStartOfDay();
        LocalDateTime endMonth = startMonth.plusYears(1);
        List<SettlementEstimateResponse> histories = settlementHistoryRepository
                .findAllBySelectorsIdAndActivityMonthGreaterThanEqualAndActivityMonthLessThanOrderByActivityMonthDesc(
                        selectors.getId(), startMonth, endMonth)
                .stream()
                .map(history -> SettlementEstimateResponse.of(history, selectors))
                .toList();

        return new SettlementHistoryListResponse(
                selectedYear,
                settlementHistoryRepository.findAvailableYearsBySelectorsId(selectors.getId()),
                histories);
    }

    private YearMonth resolveReadableMonth(YearMonth requestedMonth) {
        YearMonth previousMonth = YearMonth.from(LocalDate.now(clock)).minusMonths(1);
        YearMonth resolved = requestedMonth == null ? previousMonth : requestedMonth;
        if (resolved.isAfter(previousMonth)) {
            throw new BusinessException(
                    ErrorCode.INVALID_INPUT,
                    "현재 활동월 이후의 정산 예상액은 조회할 수 없습니다.");
        }
        return resolved;
    }
}
