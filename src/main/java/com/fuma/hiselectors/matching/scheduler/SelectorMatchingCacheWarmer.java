package com.fuma.hiselectors.matching.scheduler;

import com.fuma.hiselectors.campaign.model.Campaign;
import com.fuma.hiselectors.campaign.repository.CampaignRepository;
import com.fuma.hiselectors.matching.service.SelectorMatchingService;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 캠페인별 추천 셀렉터스 로드가 느려, 진행 중인 캠페인의 추천 결과를 미리 계산해 캐시에 채운다.
 * 관리자가 조회할 땐 캐시 히트로 즉시 응답한다. 부팅 직후와 주기적으로 무효화 후 재적재한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        name = "selector-matching.cache-warm.enabled",
        havingValue = "true",
        matchIfMissing = true)
public class SelectorMatchingCacheWarmer {

    private final SelectorMatchingService matchingService;
    private final CampaignRepository campaignRepository;
    private final Clock clock;

    @Scheduled(
            initialDelayString = "${selector-matching.cache-warm.initial-delay:10000}",
            fixedDelayString = "${selector-matching.cache-warm.fixed-delay:1800000}")
    public void warm() {
        matchingService.evictAll();
        LocalDate today = LocalDate.now(clock);
        List<Campaign> active = campaignRepository.findAllByIsDeletedFalseOrderByIdDesc().stream()
                .filter(campaign -> !campaign.getEndDate().isBefore(today))
                .toList();
        for (Campaign campaign : active) {
            try {
                matchingService.recommend(null, null, campaign.getId(), null, null, null);
            } catch (RuntimeException e) {
                log.warn("추천 캐시 예열 실패: campaignId={}", campaign.getId(), e);
            }
        }
        log.info("추천 캐시 예열 완료: campaigns={}", active.size());
    }
}
