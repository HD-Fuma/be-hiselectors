package com.fuma.hiselectors.settlement.service;

import com.fuma.hiselectors.purchase.repository.PurchaseHistoryRepository;
import com.fuma.hiselectors.settlement.dto.SettlementPurchaseHistoryResponse;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SettlementPurchaseHistoryService {

    private final PurchaseHistoryRepository purchaseHistoryRepository;
    private final Clock clock;

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
}
