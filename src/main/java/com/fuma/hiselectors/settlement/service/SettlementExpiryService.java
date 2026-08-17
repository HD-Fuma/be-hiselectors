package com.fuma.hiselectors.settlement.service;

import com.fuma.hiselectors.settlement.model.SettlementHistory;
import com.fuma.hiselectors.settlement.model.SettlementStatus;
import com.fuma.hiselectors.settlement.repository.SettlementHistoryRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.EnumSet;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class SettlementExpiryService {

    private final SettlementHistoryRepository settlementHistoryRepository;
    private final SettlementExpiryWorker settlementExpiryWorker;
    private final Clock clock;

    @Transactional
    public int expireLongTermHolds() {
        LocalDateTime cutoff = LocalDateTime.now(clock).minusMonths(12);
        LocalDateTime expiredAt = LocalDateTime.now(clock);
        var histories = settlementHistoryRepository.findAllByStatusInAndUpdatedAtLessThanEqual(
                EnumSet.of(SettlementStatus.PAYMENT_HOLD_INFO, SettlementStatus.PAYMENT_HOLD_BLACK),
                cutoff);
        int expiredCount = 0;
        for (SettlementHistory history : histories) {
            try {
                if (settlementExpiryWorker.expireIfEligible(history.getId(), cutoff, expiredAt)) {
                    expiredCount++;
                }
            } catch (RuntimeException e) {
                log.error("장기 보류 정산 만료 처리 실패: settlementId={}", history.getId(), e);
            }
        }
        return expiredCount;
    }
}
