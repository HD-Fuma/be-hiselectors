package com.fuma.hiselectors.performance.service;

import com.fuma.hiselectors.exception.BusinessException;
import com.fuma.hiselectors.exception.ErrorCode;
import com.fuma.hiselectors.generation.model.Generation;
import com.fuma.hiselectors.generation.model.GenerationStatus;
import com.fuma.hiselectors.generation.repository.GenerationRepository;
import com.fuma.hiselectors.performance.dto.DashboardSalesSettlementTrendResponse;
import com.fuma.hiselectors.performance.dto.DashboardSalesSettlementTrendResponse.Point;
import com.fuma.hiselectors.performance.repository.SelectorPerformanceQueryRepository;
import com.fuma.hiselectors.performance.repository.SelectorPerformanceQueryRepository.DatedSelectorSales;
import com.fuma.hiselectors.performance.repository.SelectorPerformanceQueryRepository.SelectorSnsProfile;
import com.fuma.hiselectors.selectors.model.Selectors;
import com.fuma.hiselectors.settlement.service.CommissionRateCalculator;
import java.math.BigDecimal;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DashboardSalesSettlementTrendService {

    private static final int MAX_DAYS = 7;

    private final SelectorPerformanceQueryRepository queryRepository;
    private final GenerationRepository generationRepository;
    private final CommissionRateCalculator commissionRateCalculator;

    public DashboardSalesSettlementTrendResponse getTrend(
            LocalDate startDate, LocalDate endDate) {
        validatePeriod(startDate, endDate);

        List<Long> generationIds = generationRepository
                .findAllByStatusOrderByActivityStartDateAscIdAsc(GenerationStatus.ACTIVE)
                .stream()
                .map(Generation::getId)
                .toList();
        List<Selectors> selectors = queryRepository.findVisibleMembers(generationIds);
        if (selectors.isEmpty()) {
            return responseWithEmptyPoints(startDate, endDate);
        }

        List<Long> selectorIds = selectors.stream().map(Selectors::getId).toList();
        Map<Long, SelectorSnsProfile> profiles = queryRepository.findSnsProfiles(selectorIds)
                .stream()
                .collect(Collectors.toMap(
                        SelectorSnsProfile::selectorId,
                        Function.identity(),
                        (existing, ignored) -> existing));
        List<DatedSelectorSales> rows = queryRepository
                .summarizeConfirmedSalesBySelectorAndDay(
                        selectorIds,
                        startDate.atStartOfDay(),
                        endDate.plusDays(1).atStartOfDay());

        Map<LocalDate, DailyAmounts> byDate = new LinkedHashMap<>();
        for (DatedSelectorSales row : rows) {
            SelectorSnsProfile profile = profiles.get(row.selectorId());
            BigDecimal rate = profile == null || profile.snsCode() == null
                    ? BigDecimal.ZERO
                    : commissionRateCalculator.calculate(
                            profile.snsCode(), profile.followerCount());
            BigDecimal settlement = SelectorPerformanceDashboardCalculator.accruedCommission(
                    row.totalSales(), rate);
            byDate.computeIfAbsent(row.date(), ignored -> new DailyAmounts())
                    .add(row.totalSales(), settlement);
        }

        List<Point> points = new ArrayList<>();
        for (LocalDate date = startDate; !date.isAfter(endDate); date = date.plusDays(1)) {
            DailyAmounts amounts = byDate.get(date);
            points.add(amounts == null
                    ? new Point(date, BigDecimal.ZERO, BigDecimal.ZERO)
                    : new Point(date, amounts.sales, amounts.settlement));
        }
        return new DashboardSalesSettlementTrendResponse(
                startDate, endDate, List.copyOf(points));
    }

    private DashboardSalesSettlementTrendResponse responseWithEmptyPoints(
            LocalDate startDate, LocalDate endDate) {
        List<Point> points = new ArrayList<>();
        for (LocalDate date = startDate; !date.isAfter(endDate); date = date.plusDays(1)) {
            points.add(new Point(date, BigDecimal.ZERO, BigDecimal.ZERO));
        }
        return new DashboardSalesSettlementTrendResponse(
                startDate, endDate, List.copyOf(points));
    }

    private void validatePeriod(LocalDate startDate, LocalDate endDate) {
        if (startDate == null || endDate == null) {
            throw new BusinessException(
                    ErrorCode.INVALID_INPUT, "대시보드 조회 시작일과 종료일은 필수입니다.");
        }
        try {
            long days = ChronoUnit.DAYS.between(startDate, endDate) + 1L;
            if (days < 1L || days > MAX_DAYS) {
                throw new BusinessException(
                        ErrorCode.INVALID_INPUT, "대시보드 조회 기간은 1일 이상 7일 이하여야 합니다.");
            }
            endDate.plusDays(1);
        } catch (DateTimeException exception) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "조회 기간이 올바르지 않습니다.");
        }
    }

    private static final class DailyAmounts {

        private BigDecimal sales = BigDecimal.ZERO;
        private BigDecimal settlement = BigDecimal.ZERO;

        private void add(BigDecimal salesAmount, BigDecimal settlementAmount) {
            sales = sales.add(salesAmount);
            settlement = settlement.add(settlementAmount);
        }
    }
}
