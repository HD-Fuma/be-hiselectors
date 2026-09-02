package com.fuma.hiselectors.settlement.service;

import com.fuma.hiselectors.exception.BusinessException;
import com.fuma.hiselectors.exception.ErrorCode;
import com.fuma.hiselectors.purchase.repository.PurchaseHistoryRepository;
import com.fuma.hiselectors.settlement.dto.SettlementPurchaseHistoryCursorResponse;
import com.fuma.hiselectors.settlement.dto.SettlementPurchaseHistoryResponse;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SettlementPurchaseHistoryService {

    private final PurchaseHistoryRepository purchaseHistoryRepository;
    private final Clock clock;
    private final PurchaseHistoryCursorCodec cursorCodec;

    public Page<SettlementPurchaseHistoryResponse> search(
            Long selectorsId,
            YearMonth requestedMonth,
            boolean allMonths,
            Pageable pageable) {
        if (allMonths) {
            return purchaseHistoryRepository.searchForSettlementAdmin(
                    selectorsId, null, null, pageable);
        }

        YearMonth month = requestedMonth == null
                ? YearMonth.from(LocalDate.now(clock)).minusMonths(1)
                : requestedMonth;
        return purchaseHistoryRepository.searchForSettlementAdmin(
                selectorsId,
                month.atDay(1).atStartOfDay(),
                month.plusMonths(1).atDay(1).atStartOfDay(),
                pageable);
    }

    public SettlementPurchaseHistoryCursorResponse searchCursor(
            Long selectorsId,
            YearMonth requestedMonth,
            boolean allMonths,
            String encodedCursor,
            int size) {
        if (size < 1 || size > 100) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "size는 1 이상 100 이하여야 합니다.");
        }

        PurchaseHistoryCursor cursor = encodedCursor == null || encodedCursor.isBlank()
                ? null
                : cursorCodec.decode(encodedCursor);
        DateRange dateRange = resolveDateRange(requestedMonth, allMonths);
        LocalDateTime cursorPurchasedAt = cursor == null ? null : cursor.purchasedAt();
        Long cursorPurchaseHistoryId = cursor == null ? null : cursor.purchaseHistoryId();
        Pageable limit = PageRequest.of(0, size + 1);

        List<SettlementPurchaseHistoryResponse> rows = selectorsId == null
                ? purchaseHistoryRepository.searchCursorForSettlementAdmin(
                        dateRange.startInclusive(),
                        dateRange.endExclusive(),
                        cursorPurchasedAt,
                        cursorPurchaseHistoryId,
                        limit)
                : purchaseHistoryRepository.searchCursorForSettlementAdminBySelectorsId(
                        selectorsId,
                        dateRange.startInclusive(),
                        dateRange.endExclusive(),
                        cursorPurchasedAt,
                        cursorPurchaseHistoryId,
                        limit);

        boolean hasNext = rows.size() > size;
        List<SettlementPurchaseHistoryResponse> content = hasNext
                ? rows.subList(0, size)
                : rows;
        String nextCursor = hasNext
                ? cursorCodec.encode(
                        content.getLast().purchasedAt(),
                        content.getLast().purchaseHistoryId())
                : null;
        return new SettlementPurchaseHistoryCursorResponse(content, nextCursor, hasNext);
    }

    private DateRange resolveDateRange(YearMonth requestedMonth, boolean allMonths) {
        if (allMonths) {
            return new DateRange(null, null);
        }
        YearMonth month = requestedMonth == null
                ? YearMonth.from(LocalDate.now(clock)).minusMonths(1)
                : requestedMonth;
        return new DateRange(
                month.atDay(1).atStartOfDay(),
                month.plusMonths(1).atDay(1).atStartOfDay());
    }

    private record DateRange(LocalDateTime startInclusive, LocalDateTime endExclusive) {
    }
}
