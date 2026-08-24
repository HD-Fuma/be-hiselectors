package com.fuma.hiselectors.campaign.service;

import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fuma.hiselectors.campaign.repository.CampaignRepository;
import com.fuma.hiselectors.media.service.CampaignThumbnailStorage;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;

@SpringJUnitConfig(CampaignThumbnailRemovalTransactionTest.TestConfiguration.class)
class CampaignThumbnailRemovalTransactionTest {

    private static final String URL =
            "https://media.hiselectors.shop/campaigns/123e4567-e89b-12d3-a456-426614174000.png";

    @Autowired private ApplicationEventPublisher eventPublisher;
    @Autowired private CampaignThumbnailStorage storage;
    @Autowired private CampaignRepository campaignRepository;
    @Autowired private TransactionTemplate transactions;

    @BeforeEach
    void resetMocks() {
        clearInvocations(storage, campaignRepository);
        when(campaignRepository.existsByThumbnailUrl(URL)).thenReturn(false);
    }

    @Test
    void deletesOnlyAfterTransactionCommit() {
        transactions.executeWithoutResult(status -> {
            eventPublisher.publishEvent(new CampaignThumbnailRemovalRequested(URL));
            verify(storage, never()).delete(URL);
        });

        verify(storage).delete(URL);
    }

    @Test
    void skipsDeleteAfterTransactionRollback() {
        transactions.executeWithoutResult(status -> {
            eventPublisher.publishEvent(new CampaignThumbnailRemovalRequested(URL));
            status.setRollbackOnly();
        });

        verify(storage, never()).delete(URL);
    }

    @Configuration
    @EnableTransactionManagement
    static class TestConfiguration {

        @Bean
        CampaignThumbnailStorage storage() {
            return mock(CampaignThumbnailStorage.class);
        }

        @Bean
        CampaignRepository campaignRepository() {
            return mock(CampaignRepository.class);
        }

        @Bean
        CampaignThumbnailRemovalListener listener(CampaignThumbnailStorage storage,
                                                   CampaignRepository campaignRepository) {
            return new CampaignThumbnailRemovalListener(storage, campaignRepository);
        }

        @Bean
        DataSource dataSource() {
            return new DriverManagerDataSource(
                    "jdbc:h2:mem:thumbnail-removal-events;DB_CLOSE_DELAY=-1", "sa", "");
        }

        @Bean
        PlatformTransactionManager transactionManager(DataSource dataSource) {
            return new DataSourceTransactionManager(dataSource);
        }

        @Bean
        TransactionTemplate transactionTemplate(PlatformTransactionManager transactionManager) {
            return new TransactionTemplate(transactionManager);
        }
    }
}
