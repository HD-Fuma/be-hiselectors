package com.fuma.hiselectors.settlement.service;

import com.fuma.hiselectors.settlement.model.SettlementHistory;
import com.fuma.hiselectors.settlement.model.SettlementStatus;
import com.fuma.hiselectors.settlement.repository.SettlementHistoryRepository;
import java.time.LocalDateTime;
import java.util.EnumSet;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SettlementExpiryWorker {

    private final SettlementHistoryRepository settlementHistoryRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean expireIfEligible(Long settlementId, LocalDateTime cutoff, LocalDateTime expiredAt) {
        SettlementHistory history = settlementHistoryRepository.findByIdForUpdate(settlementId)
                .orElse(null);
        if (history == null
                || !EnumSet.of(SettlementStatus.PAYMENT_HOLD_INFO, SettlementStatus.PAYMENT_HOLD_BLACK)
                .contains(history.getStatus())
                || history.getUpdatedAt() == null
                || history.getUpdatedAt().isAfter(cutoff)) {
            return false;
        }

        history.transitionTo(SettlementStatus.EXPIRED, expiredAt);
        return true;
    }
}
