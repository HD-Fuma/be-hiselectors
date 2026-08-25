package com.fuma.hiselectors.settlement.service;

import com.fuma.hiselectors.exception.BusinessException;
import com.fuma.hiselectors.exception.ErrorCode;
import com.fuma.hiselectors.purchase.model.PurchaseStatus;
import com.fuma.hiselectors.purchase.repository.PurchaseHistoryRepository;
import com.fuma.hiselectors.selectors.model.Selectors;
import com.fuma.hiselectors.selectors.model.SelectorsSnsAccount;
import com.fuma.hiselectors.selectors.repository.SelectorsRepository;
import com.fuma.hiselectors.selectors.repository.SelectorsSnsAccountRepository;
import com.fuma.hiselectors.settlement.dto.SettlementAdminSummaryResponse;
import com.fuma.hiselectors.settlement.dto.SelectorSettlementDetailResponse;
import com.fuma.hiselectors.settlement.dto.SettlementEstimateResponse;
import com.fuma.hiselectors.settlement.model.SettlementAccount;
import com.fuma.hiselectors.settlement.model.SettlementHistory;
import com.fuma.hiselectors.settlement.model.SettlementStatus;
import com.fuma.hiselectors.settlement.repository.SettlementAccountRepository;
import com.fuma.hiselectors.settlement.repository.SettlementHistoryRepository;
import com.fuma.hiselectors.settlement.security.SettlementAccountCrypto;
import java.math.BigDecimal;
import java.math.RoundingMode;
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

    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");

    private final SettlementHistoryRepository settlementHistoryRepository;
    private final SettlementAccountRepository settlementAccountRepository;
    private final SelectorsRepository selectorsRepository;
    private final SelectorsSnsAccountRepository selectorsSnsAccountRepository;
    private final PurchaseHistoryRepository purchaseHistoryRepository;
    private final Clock clock;
    private final SettlementProvisionalEstimateService provisionalEstimateService;
    private final SettlementAccountCrypto accountCrypto;

    private static final List<PurchaseStatus> VALID_PURCHASE_CONVERSION_STATUSES = List.of(
            PurchaseStatus.PURCHASED, PurchaseStatus.PURCHASE_CONFIRMED);
    private static final List<SettlementStatus> ACTIVE_PAYMENT_STATUSES = List.of(
            SettlementStatus.PAYMENT_HOLD_BLACK,
            SettlementStatus.PAYMENT_HOLD_INFO,
            SettlementStatus.PAYMENT_PENDING,
            SettlementStatus.PAYMENT_CARRYOVER,
            SettlementStatus.CALCULATING);

    public Page<SettlementEstimateResponse> search(
            YearMonth requestedMonth,
            Long selectorsId,
            SettlementStatus status,
            Pageable pageable) {
        YearMonth activityMonth = resolveActivityMonth(requestedMonth);
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

    public SettlementAdminSummaryResponse summarize(
            YearMonth requestedMonth, Long selectorsId, SettlementStatus status) {
        YearMonth activityMonth = resolveActivityMonth(requestedMonth);
        SettlementHistoryRepository.SettlementAggregate aggregate = settlementHistoryRepository
                .summarize(toYearMonthKey(activityMonth), selectorsId, status);
        long confirmedSalesAmount = aggregate.getConfirmedSalesAmount();
        long settlementAmount = aggregate.getSettlementAmount();
        BigDecimal commissionToSalesRate = confirmedSalesAmount == 0L
                ? new BigDecimal("0.00")
                : BigDecimal.valueOf(settlementAmount)
                        .multiply(ONE_HUNDRED)
                        .divide(BigDecimal.valueOf(confirmedSalesAmount), 2, RoundingMode.HALF_UP);

        return new SettlementAdminSummaryResponse(
                activityMonth,
                aggregate.getSettlementCount(),
                aggregate.getConfirmedPurchaseCount(),
                confirmedSalesAmount,
                settlementAmount,
                commissionToSalesRate);
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
        SettlementAccount settlementAccount = settlementAccountRepository
                .findFirstBySelectorsIdAndDeletedFalseOrderByIdDesc(selectorsId)
                .orElse(null);
        YearMonth currentMonth = YearMonth.from(LocalDate.now(clock));
        YearMonth paymentMonth = currentMonth;
        List<SettlementHistory> pendingHistories = settlementHistoryRepository
                .findAllBySelectorsIdAndStatus(selectorsId, SettlementStatus.PAYMENT_PENDING);
        List<SettlementHistory> carryoverHistories = settlementHistoryRepository
                .findAllBySelectorsIdAndStatus(selectorsId, SettlementStatus.PAYMENT_CARRYOVER);
        long scheduledCommission = java.util.stream.Stream.concat(
                        pendingHistories.stream(), carryoverHistories.stream())
                .mapToLong(SettlementHistory::getSettlementAmount)
                .sum();
        paymentMonth = nextPaymentMonth(pendingHistories);
        SettlementStatus paymentStatus = currentPaymentStatus(
                settlementHistoryRepository.findAllBySelectorsIdAndStatusIn(
                        selectorsId, ACTIVE_PAYMENT_STATUSES));

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
                        scheduledCommission,
                        paymentMonth,
                        paymentStatus,
                        settlementHistoryRepository.sumSalesBySelectorsId(selectorsId));

        return SelectorSettlementDetailResponse.of(
                isAccountRegistered(settlementAccount),
                selectors, snsAccount, summary, getHistories(selectorsId, selectors, pageable));
    }

    private boolean isAccountRegistered(SettlementAccount account) {
        return account != null
                && !isBlank(account.getBankName())
                && !isBlank(accountCrypto.decrypt(account.getAccountNumberEncrypted()))
                && !isBlank(account.getAccountHolder());
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private Page<SettlementEstimateResponse> getHistories(
            Long selectorsId, Selectors selectors, Pageable pageable) {
        return settlementHistoryRepository
                .findAllBySelectorsIdOrderByActivityMonthDesc(selectorsId, pageable)
                .map(history -> toResponse(history, selectors));
    }

    private YearMonth resolveActivityMonth(YearMonth requestedMonth) {
        return requestedMonth == null
                ? YearMonth.from(LocalDate.now(clock)).minusMonths(1)
                : requestedMonth;
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

    private SettlementStatus currentPaymentStatus(List<SettlementHistory> histories) {
        if (hasStatus(histories, SettlementStatus.PAYMENT_HOLD_BLACK)) {
            return SettlementStatus.PAYMENT_HOLD_BLACK;
        }
        if (hasStatus(histories, SettlementStatus.PAYMENT_HOLD_INFO)) {
            return SettlementStatus.PAYMENT_HOLD_INFO;
        }
        if (hasStatus(histories, SettlementStatus.PAYMENT_PENDING)) {
            return SettlementStatus.PAYMENT_PENDING;
        }
        if (hasStatus(histories, SettlementStatus.PAYMENT_CARRYOVER)) {
            return SettlementStatus.PAYMENT_CARRYOVER;
        }
        if (hasStatus(histories, SettlementStatus.CALCULATING)) {
            return SettlementStatus.CALCULATING;
        }
        return null;
    }

    private YearMonth nextPaymentMonth(List<SettlementHistory> pendingHistories) {
        return pendingHistories.stream()
                .map(history -> history.getScheduledPaymentYearMonth() == null
                        ? YearMonth.from(history.getActivityMonth()).plusMonths(2)
                        : YearMonth.of(history.getScheduledPaymentYearMonth() / 100,
                                history.getScheduledPaymentYearMonth() % 100))
                .min(YearMonth::compareTo)
                .orElse(null);
    }

    private boolean hasStatus(List<SettlementHistory> histories, SettlementStatus status) {
        return histories.stream().anyMatch(history -> history.getStatus() == status);
    }

    private int toYearMonthKey(YearMonth yearMonth) {
        return yearMonth.getYear() * 100 + yearMonth.getMonthValue();
    }
}
