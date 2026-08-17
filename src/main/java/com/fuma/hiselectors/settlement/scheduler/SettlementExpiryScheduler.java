package com.fuma.hiselectors.settlement.scheduler;

import com.fuma.hiselectors.settlement.service.SettlementExpiryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class SettlementExpiryScheduler {

    private final SettlementExpiryService settlementExpiryService;

    @Scheduled(
            cron = "${settlement.expiry.cron:0 0 0 1 * *}",
            zone = "${settlement.zone:Asia/Seoul}")
    public void expireLongTermHolds() {
        int expiredCount = settlementExpiryService.expireLongTermHolds();
        log.info("장기 지급보류 정산 소멸 배치 완료: expired={}", expiredCount);
    }
}
