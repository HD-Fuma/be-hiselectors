package com.fuma.hiselectors.performance.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fuma.hiselectors.campaign.model.Campaign;
import com.fuma.hiselectors.campaign.repository.CampaignRepository;
import com.fuma.hiselectors.exception.BusinessException;
import com.fuma.hiselectors.performance.repository.CampaignPerformanceQueryRepository;
import com.fuma.hiselectors.performance.repository.CampaignPerformanceQueryRepository.AttributedPurchase;
import com.fuma.hiselectors.purchase.model.PurchaseStatus;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CampaignPerformanceAdminServiceTest {

    private final CampaignRepository campaignRepository = mock(CampaignRepository.class);
    private final CampaignPerformanceQueryRepository queryRepository =
            mock(CampaignPerformanceQueryRepository.class);
    private CampaignPerformanceAdminService service;

    @BeforeEach
    void setUp() {
        service = new CampaignPerformanceAdminService(campaignRepository, queryRepository);
    }

    @Test
    void clampsPeriodAndAggregatesSummaryDailyProductsAndSelectors() {
        Campaign campaign = campaign(
                LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31));
        when(campaignRepository.findByIdAndIsDeletedFalse(3L))
                .thenReturn(Optional.of(campaign));
        LocalDateTime start = LocalDate.of(2026, 8, 1).atStartOfDay();
        LocalDateTime end = LocalDate.of(2026, 9, 1).atStartOfDay();
        when(queryRepository.findAttributedTerminalPurchases(3L, start, end))
                .thenReturn(List.of(
                        purchase(1L, "ORD-1", 10L, "SEL-10", "첫째",
                                101L, "P-101", "상품 1", "브랜드", 2, "100",
                                PurchaseStatus.PURCHASE_CONFIRMED, "2026-08-03T10:00:00"),
                        purchase(2L, "ORD-1", 10L, "SEL-10", "첫째",
                                102L, "P-102", "상품 2", "브랜드", 1, "50",
                                PurchaseStatus.PURCHASE_CONFIRMED, "2026-08-03T11:00:00"),
                        purchase(3L, "ORD-2", 11L, "SEL-11", "둘째",
                                101L, "P-101", "상품 1", "브랜드", 1, "70",
                                PurchaseStatus.PURCHASE_CONFIRMED, "2026-08-04T10:00:00"),
                        purchase(4L, "ORD-3", 10L, "SEL-10", "첫째",
                                101L, "P-101", "상품 1", "브랜드", 1, "80",
                                PurchaseStatus.RETURNED, "2026-08-05T10:00:00"),
                        purchase(5L, "ORD-3", 10L, "SEL-10", "첫째",
                                102L, "P-102", "상품 2", "브랜드", 1, "20",
                                PurchaseStatus.CANCELED, "2026-08-05T10:01:00")));

        var result = service.getPerformance(
                3L, LocalDate.of(2026, 7, 20), LocalDate.of(2026, 9, 5));

        assertThat(result.startDate()).isEqualTo("2026-08-01");
        assertThat(result.endDate()).isEqualTo("2026-08-31");
        assertThat(result.summary().confirmedSales()).isEqualByComparingTo("220");
        assertThat(result.summary().confirmedOrderCount()).isEqualTo(2L);
        assertThat(result.summary().soldQuantity()).isEqualTo(4L);
        assertThat(result.summary().contributingSelectorCount()).isEqualTo(2L);
        assertThat(result.summary().canceledOrReturnedOrderCount()).isEqualTo(1L);
        assertThat(result.summary().canceledOrReturnedRate()).isEqualByComparingTo("33.33");
        assertThat(result.daily()).hasSize(31);
        assertThat(result.daily().getFirst().confirmedSales()).isEqualByComparingTo("0");
        assertThat(result.daily().get(2).date()).isEqualTo("2026-08-03");
        assertThat(result.daily().get(2).confirmedOrderCount()).isEqualTo(1L);
        assertThat(result.products()).extracting(item -> item.productId())
                .containsExactly(101L, 102L);
        assertThat(result.products().getFirst().confirmedSales()).isEqualByComparingTo("170");
        assertThat(result.products().getFirst().contributingSelectorCount()).isEqualTo(2L);
        assertThat(result.selectors()).extracting(item -> item.selectorId())
                .containsExactly(10L, 11L);
        assertThat(result.selectors().getFirst().confirmedSales()).isEqualByComparingTo("150");
        assertThat(result.selectors().getFirst().productCount()).isEqualTo(2L);
        assertThat(result.selectors().getFirst().profileImageUrl())
                .isEqualTo("https://cdn.example.com/SEL-10.jpg");
        verify(queryRepository).findAttributedTerminalPurchases(3L, start, end);
    }

    @Test
    void rejectsInvalidOrNonOverlappingPeriodBeforePurchaseQuery() {
        assertThatThrownBy(() -> service.getPerformance(
                3L, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 8, 31)))
                .isInstanceOf(BusinessException.class)
                .hasMessage("시작일은 종료일보다 늦을 수 없습니다.");
        verify(campaignRepository, never()).findByIdAndIsDeletedFalse(3L);

        when(campaignRepository.findByIdAndIsDeletedFalse(3L)).thenReturn(Optional.of(
                campaign(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31))));
        assertThatThrownBy(() -> service.getPerformance(
                3L, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30)))
                .isInstanceOf(BusinessException.class)
                .hasMessage("조회 기간이 캠페인 기간과 겹치지 않습니다.");
        verify(queryRepository, never()).findAttributedTerminalPurchases(
                3L, LocalDateTime.of(2026, 9, 1, 0, 0),
                LocalDateTime.of(2026, 10, 1, 0, 0));
    }

    private Campaign campaign(LocalDate startDate, LocalDate endDate) {
        return Campaign.builder()
                .title("캠페인")
                .startDate(startDate)
                .endDate(endDate)
                .build();
    }

    private AttributedPurchase purchase(
            Long purchaseId,
            String orderNo,
            Long selectorId,
            String selectorCode,
            String nickname,
            Long productId,
            String productCode,
            String productName,
            String brandName,
            int quantity,
            String paidAmount,
            PurchaseStatus status,
            String purchasedAt) {
        return new AttributedPurchase(
                purchaseId,
                orderNo,
                selectorId,
                selectorCode,
                nickname,
                "https://cdn.example.com/" + selectorCode + ".jpg",
                productId,
                productCode,
                productName,
                brandName,
                null,
                quantity,
                new BigDecimal(paidAmount),
                status,
                LocalDateTime.parse(purchasedAt));
    }
}
