package com.fuma.hiselectors.campaign.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fuma.hiselectors.campaign.dto.CampaignUpdateRequest;
import com.fuma.hiselectors.campaign.model.Campaign;
import com.fuma.hiselectors.campaign.repository.CampaignProductRepository;
import com.fuma.hiselectors.campaign.repository.CampaignRepository;
import com.fuma.hiselectors.product.repository.ProductRepository;
import jakarta.persistence.LockModeType;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.test.util.ReflectionTestUtils;

class CampaignAdminServiceTest {

    @Test
    void updateLoadsCampaignWithPessimisticWriteLock() throws Exception {
        CampaignRepository campaignRepository = mock(CampaignRepository.class);
        CampaignProductRepository campaignProductRepository = mock(CampaignProductRepository.class);
        ProductRepository productRepository = mock(ProductRepository.class);
        ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
        Campaign campaign = Campaign.builder().title("제목").description("설명")
                .startDate(LocalDate.of(2026, 8, 24)).endDate(LocalDate.of(2026, 8, 24)).build();
        ReflectionTestUtils.setField(campaign, "id", 1L);
        when(campaignRepository.findByIdAndIsDeletedFalseForUpdate(1L)).thenReturn(Optional.of(campaign));
        when(campaignProductRepository.findAllByCampaignIdOrderByIdAsc(1L)).thenReturn(List.of());
        CampaignAdminService service = new CampaignAdminService(campaignRepository, campaignProductRepository,
                productRepository, Clock.fixed(Instant.parse("2026-08-24T00:00:00Z"), ZoneOffset.UTC),
                eventPublisher);

        service.update(1L, new CampaignUpdateRequest(null, null, null, null, null, null));

        verify(campaignRepository).findByIdAndIsDeletedFalseForUpdate(1L);
        Lock lock = CampaignRepository.class
                .getMethod("findByIdAndIsDeletedFalseForUpdate", Long.class)
                .getAnnotation(Lock.class);
        assertThat(lock).isNotNull();
        assertThat(lock.value()).isEqualTo(LockModeType.PESSIMISTIC_WRITE);
    }

    @Test
    void deleteLoadsCampaignWithPessimisticWriteLock() {
        CampaignRepository campaignRepository = mock(CampaignRepository.class);
        CampaignProductRepository campaignProductRepository = mock(CampaignProductRepository.class);
        ProductRepository productRepository = mock(ProductRepository.class);
        ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
        Campaign campaign = Campaign.builder().title("제목").description("설명")
                .startDate(LocalDate.of(2026, 8, 20)).endDate(LocalDate.of(2026, 8, 23)).build();
        ReflectionTestUtils.setField(campaign, "id", 1L);
        when(campaignRepository.findByIdAndIsDeletedFalseForUpdate(1L)).thenReturn(Optional.of(campaign));
        CampaignAdminService service = new CampaignAdminService(campaignRepository, campaignProductRepository,
                productRepository, Clock.fixed(Instant.parse("2026-08-24T00:00:00Z"), ZoneOffset.UTC),
                eventPublisher);

        service.delete(1L);

        verify(campaignRepository).findByIdAndIsDeletedFalseForUpdate(1L);
        assertThat(campaign.isDeleted()).isTrue();
    }
}
