package com.fuma.hiselectors.matching.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fuma.hiselectors.campaign.model.Campaign;
import com.fuma.hiselectors.campaign.repository.CampaignProductRepository;
import com.fuma.hiselectors.campaign.repository.CampaignRepository;
import com.fuma.hiselectors.matching.repository.MatchingQueryRepository;
import com.fuma.hiselectors.performance.repository.SelectorPerformanceQueryRepository;
import com.fuma.hiselectors.product.repository.ProductRepository;
import com.fuma.hiselectors.selectors.repository.SelectorsRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SelectorMatchingServiceTest {

    private static final Clock TODAY = Clock.fixed(
            LocalDate.of(2026, 9, 2).atStartOfDay(ZoneId.of("Asia/Seoul")).toInstant(),
            ZoneId.of("Asia/Seoul"));

    @Mock MatchingQueryRepository matchingRepository;
    @Mock SelectorsRepository selectorsRepository;
    @Mock SelectorPerformanceQueryRepository performanceQueryRepository;
    @Mock ProductRepository productRepository;
    @Mock CampaignProductRepository campaignProductRepository;
    @Mock CampaignRepository campaignRepository;

    private SelectorMatchingService service() {
        return new SelectorMatchingService(matchingRepository, selectorsRepository,
                performanceQueryRepository, productRepository, campaignProductRepository,
                campaignRepository, TODAY);
    }

    @Test
    void endedCampaignReturnsNoRecommendationAndSkipsQuery() {
        when(campaignRepository.findByIdAndIsDeletedFalse(7L))
                .thenReturn(Optional.of(campaign(LocalDate.of(2026, 9, 1))));

        var result = service().recommend(null, null, 7L, null, null, null);

        assertThat(result).isEmpty();
        verifyNoInteractions(matchingRepository, campaignProductRepository);
    }

    @Test
    void missingCampaignReturnsNoRecommendation() {
        when(campaignRepository.findByIdAndIsDeletedFalse(7L)).thenReturn(Optional.empty());

        assertThat(service().recommend(null, null, 7L, null, null, null)).isEmpty();
        Mockito.verify(matchingRepository, never())
                .summarizeCategoryConfirmedSales(any(), any(), any());
    }

    private Campaign campaign(LocalDate endDate) {
        return Campaign.builder()
                .title("t").startDate(endDate.minusDays(10)).endDate(endDate).build();
    }
}
