package com.fuma.hiselectors.settlement.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.YearMonth;
import org.junit.jupiter.api.Test;

class SettlementSchedulePolicyTest {

    private final SettlementSchedulePolicy policy = new SettlementSchedulePolicy();

    @Test
    void finalizesOnTheTwentyFirstUnlessItFallsOnWeekend() {
        assertThat(policy.finalizationDate(YearMonth.of(2026, 6)))
                .isEqualTo(LocalDate.of(2026, 7, 21));
        assertThat(policy.finalizationDate(YearMonth.of(2027, 7)))
                .isEqualTo(LocalDate.of(2027, 8, 20));
        assertThat(policy.finalizationDate(YearMonth.of(2027, 1)))
                .isEqualTo(LocalDate.of(2027, 2, 19));
    }

    @Test
    void processesPaymentOnTheTwentiethUnlessItFallsOnWeekend() {
        assertThat(policy.paymentDate(YearMonth.of(2027, 5)))
                .isEqualTo(LocalDate.of(2027, 7, 20));
        assertThat(policy.paymentDate(YearMonth.of(2026, 12)))
                .isEqualTo(LocalDate.of(2027, 2, 19));
        assertThat(policy.paymentDate(YearMonth.of(2027, 4)))
                .isEqualTo(LocalDate.of(2027, 6, 18));
    }

    @Test
    void resolvesLatestPayableActivityMonthForDailyCatchUp() {
        assertThat(policy.latestPayableActivityMonth(LocalDate.of(2026, 8, 19)))
                .isEqualTo(YearMonth.of(2026, 5));
        assertThat(policy.latestPayableActivityMonth(LocalDate.of(2026, 8, 20)))
                .isEqualTo(YearMonth.of(2026, 6));
        assertThat(policy.latestPayableActivityMonth(LocalDate.of(2026, 8, 21)))
                .isEqualTo(YearMonth.of(2026, 6));
    }
}
