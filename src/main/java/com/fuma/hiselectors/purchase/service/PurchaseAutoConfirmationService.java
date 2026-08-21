package com.fuma.hiselectors.purchase.service;

import com.fuma.hiselectors.purchase.model.PurchaseStatus;
import com.fuma.hiselectors.purchase.repository.PurchaseHistoryRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PurchaseAutoConfirmationService {

    private static final int CONFIRM_AFTER_DAYS = 7;

    private final PurchaseHistoryRepository purchaseHistoryRepository;
    private final Clock clock;

    @Transactional
    public int confirmExpiredPurchases() {
        LocalDateTime now = LocalDateTime.now(clock);
        LocalDate eligibleThrough = LocalDate.now(clock).minusDays(CONFIRM_AFTER_DAYS);
        LocalDateTime cutoffExclusive = eligibleThrough.plusDays(1).atStartOfDay();

        return purchaseHistoryRepository.confirmExpiredPurchases(
                PurchaseStatus.PURCHASED,
                PurchaseStatus.PURCHASE_CONFIRMED,
                cutoffExclusive,
                now);
    }
}
