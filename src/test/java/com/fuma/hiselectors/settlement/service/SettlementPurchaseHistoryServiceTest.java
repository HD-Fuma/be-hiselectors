package com.fuma.hiselectors.settlement.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fuma.hiselectors.config.TimeConfig;
import com.fuma.hiselectors.purchase.repository.PurchaseHistoryRepository;
import com.fuma.hiselectors.settlement.dto.SettlementPurchaseHistoryResponse;
import com.fuma.hiselectors.settlement.dto.SettlementPurchaseHistoryCursorResponse;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

class SettlementPurchaseHistoryServiceTest {

    @Test
    void defaultsToPreviousMonthWhenMonthIsNotProvided() {
        PurchaseHistoryRepository repository = mock(PurchaseHistoryRepository.class);
        Clock clock = Clock.fixed(Instant.parse("2026-08-15T01:00:00Z"), TimeConfig.SEOUL_ZONE);
        SettlementPurchaseHistoryService service = new SettlementPurchaseHistoryService(
                repository, clock, new PurchaseHistoryCursorCodec());
        Pageable pageable = PageRequest.of(0, 20);

        when(repository.searchForSettlementAdmin(
                3L,
                LocalDateTime.of(2026, 7, 1, 0, 0),
                LocalDateTime.of(2026, 8, 1, 0, 0),
                pageable)).thenReturn(new PageImpl<>(List.of(), pageable, 0));

        var result = service.search(3L, null, false, pageable);

        assertThat(result).isEmpty();
        verify(repository).searchForSettlementAdmin(
                3L,
                LocalDateTime.of(2026, 7, 1, 0, 0),
                LocalDateTime.of(2026, 8, 1, 0, 0),
                pageable);
    }

    @Test
    void usesRequestedMonthAndSelectorsFilter() {
        PurchaseHistoryRepository repository = mock(PurchaseHistoryRepository.class);
        SettlementPurchaseHistoryService service = new SettlementPurchaseHistoryService(
                repository, Clock.systemUTC(), new PurchaseHistoryCursorCodec());
        Pageable pageable = PageRequest.of(0, 20);
        SettlementPurchaseHistoryResponse row = new SettlementPurchaseHistoryResponse(
                101L, 3L, "SEL-003", "selector", 20L, "buyer", "ORDER-1", "P-1",
                2, java.math.BigDecimal.valueOf(30000), LocalDateTime.of(2026, 6, 30, 10, 0),
                null, com.fuma.hiselectors.purchase.model.PurchaseStatus.PURCHASED);

        when(repository.searchForSettlementAdmin(
                3L,
                LocalDateTime.of(2026, 6, 1, 0, 0),
                LocalDateTime.of(2026, 7, 1, 0, 0),
                pageable)).thenReturn(new PageImpl<>(List.of(row), pageable, 1));

        var result = service.search(3L, YearMonth.of(2026, 6), false, pageable);

        assertThat(result.getContent()).containsExactly(row);
    }

    @Test
    void removesMonthFilterForAllMonthsLookup() {
        PurchaseHistoryRepository repository = mock(PurchaseHistoryRepository.class);
        SettlementPurchaseHistoryService service = new SettlementPurchaseHistoryService(
                repository, Clock.systemUTC(), new PurchaseHistoryCursorCodec());
        Pageable pageable = PageRequest.of(0, 20);

        when(repository.searchForSettlementAdmin(isNull(), isNull(), isNull(), eq(pageable)))
                .thenReturn(new PageImpl<>(List.of(), pageable, 0));

        service.search(null, YearMonth.of(2026, 7), true, pageable);

        verify(repository).searchForSettlementAdmin(null, null, null, pageable);
    }

    @Test
    void cursorSearchFetchesOneExtraRowAndBuildsNextCursor() {
        PurchaseHistoryRepository repository = mock(PurchaseHistoryRepository.class);
        PurchaseHistoryCursorCodec codec = new PurchaseHistoryCursorCodec();
        SettlementPurchaseHistoryService service = new SettlementPurchaseHistoryService(
                repository, Clock.systemUTC(), codec);
        LocalDateTime samePurchasedAt = LocalDateTime.of(2026, 8, 20, 12, 0);
        SettlementPurchaseHistoryResponse first = row(103L, samePurchasedAt);
        SettlementPurchaseHistoryResponse second = row(102L, samePurchasedAt);
        SettlementPurchaseHistoryResponse extra = row(
                101L, LocalDateTime.of(2026, 8, 19, 12, 0));

        when(repository.searchCursorForSettlementAdminBySelectorsId(
                eq(3L), isNull(), isNull(), isNull(), isNull(), any(Pageable.class)))
                .thenReturn(List.of(first, second, extra));

        SettlementPurchaseHistoryCursorResponse result = service.searchCursor(
                3L, null, true, null, 2);

        assertThat(result.content()).containsExactly(first, second);
        assertThat(result.hasNext()).isTrue();
        PurchaseHistoryCursor nextCursor = codec.decode(result.nextCursor());
        assertThat(nextCursor.purchasedAt()).isEqualTo(samePurchasedAt);
        assertThat(nextCursor.purchaseHistoryId()).isEqualTo(102L);
        verify(repository).searchCursorForSettlementAdminBySelectorsId(
                eq(3L), isNull(), isNull(), isNull(), isNull(),
                eq(PageRequest.of(0, 3)));
    }

    @Test
    void cursorSearchPassesBothSortKeysAndOmitsCursorOnLastPage() {
        PurchaseHistoryRepository repository = mock(PurchaseHistoryRepository.class);
        PurchaseHistoryCursorCodec codec = new PurchaseHistoryCursorCodec();
        SettlementPurchaseHistoryService service = new SettlementPurchaseHistoryService(
                repository, Clock.systemUTC(), codec);
        LocalDateTime cursorPurchasedAt = LocalDateTime.of(2026, 8, 20, 12, 0);
        String encodedCursor = codec.encode(cursorPurchasedAt, 102L);
        SettlementPurchaseHistoryResponse last = row(
                101L, LocalDateTime.of(2026, 8, 19, 12, 0));

        when(repository.searchCursorForSettlementAdminBySelectorsId(
                eq(3L), isNull(), isNull(), eq(cursorPurchasedAt), eq(102L),
                any(Pageable.class)))
                .thenReturn(List.of(last));

        SettlementPurchaseHistoryCursorResponse result = service.searchCursor(
                3L, null, true, encodedCursor, 20);

        assertThat(result.content()).containsExactly(last);
        assertThat(result.hasNext()).isFalse();
        assertThat(result.nextCursor()).isNull();
        verify(repository).searchCursorForSettlementAdminBySelectorsId(
                eq(3L), isNull(), isNull(), eq(cursorPurchasedAt), eq(102L),
                eq(PageRequest.of(0, 21)));
    }

    private SettlementPurchaseHistoryResponse row(Long id, LocalDateTime purchasedAt) {
        return new SettlementPurchaseHistoryResponse(
                id, 3L, "SEL-003", "selector", 20L, "buyer", "ORDER-" + id, "P-1",
                1, java.math.BigDecimal.valueOf(10000), purchasedAt, null,
                com.fuma.hiselectors.purchase.model.PurchaseStatus.PURCHASED);
    }
}
