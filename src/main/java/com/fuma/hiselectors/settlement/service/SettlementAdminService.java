package com.fuma.hiselectors.settlement.service;

import com.fuma.hiselectors.exception.BusinessException;
import com.fuma.hiselectors.exception.ErrorCode;
import com.fuma.hiselectors.purchase.model.PurchaseStatus;
import com.fuma.hiselectors.purchase.repository.PurchaseHistoryRepository;
import com.fuma.hiselectors.selectors.model.Selectors;
import com.fuma.hiselectors.selectors.model.SelectorsSnsAccount;
import com.fuma.hiselectors.selectors.repository.SelectorsRepository;
import com.fuma.hiselectors.selectors.repository.SelectorsSnsAccountRepository;
import com.fuma.hiselectors.settlement.dto.SelectorSettlementDetailResponse;
import com.fuma.hiselectors.settlement.dto.SettlementEstimateResponse;
import com.fuma.hiselectors.settlement.model.SettlementHistory;
import com.fuma.hiselectors.settlement.model.SettlementStatus;
import com.fuma.hiselectors.settlement.repository.SettlementHistoryRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SettlementAdminService {

    private final SettlementHistoryRepository settlementHistoryRepository;
    private final SelectorsRepository selectorsRepository;
    private final SelectorsSnsAccountRepository selectorsSnsAccountRepository;
    private final PurchaseHistoryRepository purchaseHistoryRepository;
    private final Clock clock;
    private final SettlementProvisionalEstimateService provisionalEstimateService;

    private static final List<PurchaseStatus> VALID_PURCHASE_CONVERSION_STATUSES = List.of(
            PurchaseStatus.PURCHASED, PurchaseStatus.PURCHASE_CONFIRMED);
    private static final List<SettlementStatus> NEXT_PAYMENT_ELIGIBLE_STATUSES = List.of(
            SettlementStatus.CALCULATING, SettlementStatus.PAYMENT_PENDING);

    public Page<SettlementEstimateResponse> search(
            YearMonth requestedMonth,
            Long selectorsId,
            SettlementStatus status,
            Pageable pageable) {
        YearMonth activityMonth = requestedMonth == null
                ? YearMonth.from(LocalDate.now(clock)).minusMonths(1)
                : requestedMonth;
        Page<SettlementHistory> histories = settlementHistoryRepository.search(
                toYearMonthKey(activityMonth), selectorsId, status, pageable);
        Map<Long, Selectors> selectorsById = selectorsRepository
                .findAllById(histories.stream().map(SettlementHistory::getSelectorsId).toList())
                .stream()
                .collect(Collectors.toMap(Selectors::getId, Function.identity()));

        return histories.map(history -> toResponse(
                history,
                requireSelectors(selectorsById, history.getSelectorsId())));
    }

    public Page<SettlementEstimateResponse> getHistories(Long selectorsId, Pageable pageable) {
        Selectors selectors = selectorsRepository.findById(selectorsId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SELECTOR_NOT_FOUND));

        return getHistories(selectorsId, selectors, pageable);
    }

    public SelectorSettlementDetailResponse getDetail(Long selectorsId, Pageable pageable) {
        Selectors selectors = selectorsRepository.findById(selectorsId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SELECTOR_NOT_FOUND));
        SelectorsSnsAccount snsAccount = selectorsSnsAccountRepository
                .findBySelectorsIdAndDeletedFalse(selectorsId)
                .orElse(null);
        YearMonth currentMonth = YearMonth.from(LocalDate.now(clock));
        YearMonth paymentMonth = currentMonth;
        YearMonth payableActivityMonth = currentMonth.minusMonths(2);
        SettlementHistory nextPaymentHistory = settlementHistoryRepository
                .findBySelectorsIdAndActivityYearMonthAndStatusIn(
                        selectorsId,
                        toYearMonthKey(payableActivityMonth),
                        NEXT_PAYMENT_ELIGIBLE_STATUSES)
                .orElse(null);

        long currentMonthPurchaseConversionCount = purchaseHistoryRepository
                .countDistinctOrdersBySelectorsIdAndStatusInAndPurchasedAtBetween(
                        selectorsId,
                        VALID_PURCHASE_CONVERSION_STATUSES,
                        currentMonth.atDay(1).atStartOfDay(),
                        currentMonth.plusMonths(1).atDay(1).atStartOfDay());
        SelectorSettlementDetailResponse.SettlementSummary summary =
                new SelectorSettlementDetailResponse.SettlementSummary(
                        purchaseHistoryRepository.countDistinctOrdersBySelectorsIdAndStatusIn(
                                selectorsId, VALID_PURCHASE_CONVERSION_STATUSES),
                        settlementHistoryRepository.sumCommissionBySelectorsIdAndStatus(
                                selectorsId, SettlementStatus.SETTLED),
                        currentMonthPurchaseConversionCount,
                        currentMonth,
                        nextPaymentHistory == null ? 0L : nextPaymentHistory.getSettlementAmount(),
                        paymentMonth,
                        nextPaymentHistory == null ? null : nextPaymentHistory.getStatus());

        return SelectorSettlementDetailResponse.of(
                selectors, snsAccount, summary, getHistories(selectorsId, selectors, pageable));
    }

    private Page<SettlementEstimateResponse> getHistories(
            Long selectorsId, Selectors selectors, Pageable pageable) {
        return settlementHistoryRepository
                .findAllBySelectorsIdOrderByActivityMonthDesc(selectorsId, pageable)
                .map(history -> toResponse(history, selectors));
    }

    private Selectors requireSelectors(Map<Long, Selectors> selectorsById, Long selectorsId) {
        Selectors selectors = selectorsById.get(selectorsId);
        if (selectors == null) {
            throw new BusinessException(ErrorCode.SELECTOR_NOT_FOUND);
        }
        return selectors;
    }

    private SettlementEstimateResponse toResponse(
            SettlementHistory history, Selectors selectors) {
        return SettlementEstimateResponse.of(
                history,
                selectors,
                provisionalEstimateService.calculate(history));
    }

    private int toYearMonthKey(YearMonth yearMonth) {
        return yearMonth.getYear() * 100 + yearMonth.getMonthValue();
    }
}
