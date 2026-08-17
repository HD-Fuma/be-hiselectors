package com.fuma.hiselectors.settlement.service;

import com.fuma.hiselectors.settlement.model.SettlementHistory;
import com.fuma.hiselectors.settlement.model.SettlementStatus;
import com.fuma.hiselectors.settlement.repository.SettlementHistoryRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.EnumSet;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SettlementExpiryService {

    private final SettlementHistoryRepository settlementHistoryRepository;
    private final Clock clock;

    @Transactional
    public int expireLongTermHolds() {
        LocalDateTime cutoff = LocalDateTime.now(clock).minusMonths(12);
        var histories = settlementHistoryRepository.findAllByStatusInAndUpdatedAtLessThanEqual(
                EnumSet.of(SettlementStatus.PAYMENT_HOLD_INFO, SettlementStatus.PAYMENT_HOLD_BLACK),
                cutoff);
        histories.forEach(history -> history.transitionTo(SettlementStatus.EXPIRED,
                LocalDateTime.now(clock)));
        return histories.size();
    }
}
