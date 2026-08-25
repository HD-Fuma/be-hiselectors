package com.fuma.hiselectors.settlement.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fuma.hiselectors.exception.BusinessException;
import com.fuma.hiselectors.exception.ErrorCode;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class SettlementHistoryTest {

    @Test
    void normalizesActivityMonthToTheFirstDayAtMidnight() {
        SettlementHistory history = SettlementHistory.create(
                1L, LocalDateTime.of(2026, 7, 21, 14, 30));

        assertThat(history.getActivityMonth())
                .isEqualTo(LocalDateTime.of(2026, 7, 1, 0, 0));
        assertThat(history.getActivityYearMonth()).isEqualTo(202607);
    }

    @Test
    void supportsDefinedStatusTransitions() {
        SettlementHistory history = calculatedHistory();

        history.transitionTo(SettlementStatus.PAYMENT_PENDING, LocalDateTime.now());
        history.transitionTo(SettlementStatus.PAYMENT_HOLD_INFO, LocalDateTime.now());
        history.transitionTo(SettlementStatus.PAYMENT_PENDING, LocalDateTime.now());
        LocalDateTime settledAt = LocalDateTime.of(2026, 9, 20, 10, 0);
        history.transitionTo(SettlementStatus.SETTLED, settledAt);

        assertThat(history.getStatus()).isEqualTo(SettlementStatus.SETTLED);
        assertThat(history.getSettledAt()).isEqualTo(settledAt);
    }

    @Test
    void calculatingHistoryCannotMoveDirectlyToPaymentHold() {
        SettlementHistory history = calculatedHistory();

        assertThatThrownBy(() -> history.transitionTo(
                SettlementStatus.PAYMENT_HOLD_INFO, LocalDateTime.now()))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_SETTLEMENT_STATUS_TRANSITION);
    }

    @Test
    void calculatingHistoryCanMoveDirectlyToSettled() {
        SettlementHistory history = calculatedHistory();
        LocalDateTime settledAt = LocalDateTime.of(2026, 8, 20, 0, 0);

        history.transitionTo(SettlementStatus.SETTLED, settledAt);

        assertThat(history.getStatus()).isEqualTo(SettlementStatus.SETTLED);
        assertThat(history.getSettledAt()).isEqualTo(settledAt);
    }

    @Test
    void settledHistoryCannotChangeOrRecalculate() {
        SettlementHistory history = calculatedHistory();
        history.transitionTo(SettlementStatus.PAYMENT_PENDING, LocalDateTime.now());
        history.transitionTo(SettlementStatus.SETTLED, LocalDateTime.now());

        assertThatThrownBy(() -> history.transitionTo(
                SettlementStatus.PAYMENT_HOLD_INFO, LocalDateTime.now()))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_SETTLEMENT_STATUS_TRANSITION);
        assertThatThrownBy(() -> history.updateCalculation(
                100L, 1L, new BigDecimal("3.00"), 3L,
                LocalDateTime.now()))
                .isInstanceOf(BusinessException.class);
    }

    private SettlementHistory calculatedHistory() {
        SettlementHistory history = SettlementHistory.create(
                1L, LocalDateTime.of(2026, 7, 1, 0, 0));
        history.updateCalculation(
                10_000L,
                2L,
                new BigDecimal("3.00"),
                300L,
                LocalDateTime.of(2026, 8, 1, 3, 0));
        return history;
    }
}
