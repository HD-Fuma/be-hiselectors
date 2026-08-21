package com.fuma.hiselectors.settlement.service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import org.springframework.stereotype.Component;

/** 활동월 정산·지급일을 주말만 고려하여 계산한다. */
@Component
public class SettlementSchedulePolicy {

    private static final int FINALIZATION_DAY = 21;
    private static final int PAYMENT_DAY = 20;

    public LocalDate finalizationDate(YearMonth activityMonth) {
        return previousWeekday(activityMonth.plusMonths(1).atDay(FINALIZATION_DAY));
    }

    public LocalDate paymentDate(YearMonth activityMonth) {
        return previousWeekday(activityMonth.plusMonths(2).atDay(PAYMENT_DAY));
    }

    public boolean isFinalizationDate(LocalDate date) {
        YearMonth activityMonth = YearMonth.from(date).minusMonths(1);
        return finalizationDate(activityMonth).equals(date);
    }

    public boolean isPaymentDate(LocalDate date) {
        YearMonth activityMonth = YearMonth.from(date).minusMonths(2);
        return paymentDate(activityMonth).equals(date);
    }

    /** 오늘 지급일이 도래한 가장 최근 활동월. 지급일 전에는 직전 활동월까지만 허용한다. */
    public YearMonth latestPayableActivityMonth(LocalDate date) {
        YearMonth candidate = YearMonth.from(date).minusMonths(2);
        return date.isBefore(paymentDate(candidate)) ? candidate.minusMonths(1) : candidate;
    }

    private LocalDate previousWeekday(LocalDate date) {
        return switch (date.getDayOfWeek()) {
            case SATURDAY -> date.minusDays(1);
            case SUNDAY -> date.minusDays(2);
            default -> date;
        };
    }
}
