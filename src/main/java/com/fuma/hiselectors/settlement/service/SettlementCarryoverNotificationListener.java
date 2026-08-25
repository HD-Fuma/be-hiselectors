package com.fuma.hiselectors.settlement.service;

import com.fuma.hiselectors.settlement.event.SettlementCarryoverConfirmedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class SettlementCarryoverNotificationListener {

    private final SettlementStatusNotificationService notificationService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void notifyCarryover(SettlementCarryoverConfirmedEvent event) {
        try {
            notificationService.notifyCarryover(
                    event.settlementId(), event.accumulatedAmount(), event.minimumPaymentAmount());
        } catch (RuntimeException exception) {
            log.warn("정산 이월 알림 호출 실패: settlementId={}", event.settlementId(), exception);
        }
    }
}
