package com.fuma.hiselectors.creator.discovery.batch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fuma.hiselectors.creator.discovery.InstagramDiscoveryService;
import com.fuma.hiselectors.creator.discovery.dto.InstagramDiscoveryResult;
import com.fuma.hiselectors.creator.model.CreatorDiscoveryInfo;
import com.fuma.hiselectors.creator.repository.CreatorDiscoveryInfoRepository;
import com.fuma.hiselectors.exception.BusinessException;
import com.fuma.hiselectors.exception.ErrorCode;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class InstagramDiscoveryBatchServiceTest {

    private CreatorDiscoveryInfoRepository discoveryInfoRepository;
    private InstagramDiscoveryService instagramDiscoveryService;
    private InstagramDiscoveryBatchService service;

    @BeforeEach
    void setUp() {
        discoveryInfoRepository = mock(CreatorDiscoveryInfoRepository.class);
        instagramDiscoveryService = mock(InstagramDiscoveryService.class);
        service = new InstagramDiscoveryBatchService(
                discoveryInfoRepository, instagramDiscoveryService);
    }

    @Test
    void continuesAfterIndividualFailureAndSkipsBlankHandles() {
        List<CreatorDiscoveryInfo> candidates = List.of(
                candidate(1L, "created"),
                candidate(2L, "not_business"),
                candidate(3L, "updated"),
                candidate(4L, " ")
        );
        when(discoveryInfoRepository
                .findByCreatorPoolSnsCodeAndCreatorPoolDeletedFalseAndIgHandleIsNotNullOrderByIdAsc(
                        "YOUTUBE"))
                .thenReturn(candidates);
        when(instagramDiscoveryService.discoverFromYoutubeCreator(1L))
                .thenReturn(discoveryResult(1L, true));
        when(instagramDiscoveryService.discoverFromYoutubeCreator(2L))
                .thenThrow(new BusinessException(
                        ErrorCode.INSTAGRAM_DISCOVERY_ACCOUNT_NOT_FOUND));
        when(instagramDiscoveryService.discoverFromYoutubeCreator(3L))
                .thenReturn(discoveryResult(3L, false));

        InstagramDiscoveryBatchResult result = service.run();

        assertThat(result).isEqualTo(new InstagramDiscoveryBatchResult(
                3, 2, 1, 1, 1));
        verify(instagramDiscoveryService).discoverFromYoutubeCreator(3L);
    }

    @Test
    void propagatesMissingMetaConfiguration() {
        CreatorDiscoveryInfo candidate = candidate(1L, "creator");
        when(discoveryInfoRepository
                .findByCreatorPoolSnsCodeAndCreatorPoolDeletedFalseAndIgHandleIsNotNullOrderByIdAsc(
                        "YOUTUBE"))
                .thenReturn(List.of(candidate));
        when(instagramDiscoveryService.discoverFromYoutubeCreator(1L))
                .thenThrow(new BusinessException(ErrorCode.META_GRAPH_CONFIG_MISSING));

        assertThatThrownBy(service::run)
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.META_GRAPH_CONFIG_MISSING);
    }

    @Test
    void propagatesMetaApiFailure() {
        CreatorDiscoveryInfo candidate = candidate(1L, "creator");
        when(discoveryInfoRepository
                .findByCreatorPoolSnsCodeAndCreatorPoolDeletedFalseAndIgHandleIsNotNullOrderByIdAsc(
                        "YOUTUBE"))
                .thenReturn(List.of(candidate));
        when(instagramDiscoveryService.discoverFromYoutubeCreator(1L))
                .thenThrow(new BusinessException(ErrorCode.META_GRAPH_API_CALL_FAILED));

        assertThatThrownBy(service::run)
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.META_GRAPH_API_CALL_FAILED);
    }

    private CreatorDiscoveryInfo candidate(Long id, String handle) {
        CreatorDiscoveryInfo candidate = mock(CreatorDiscoveryInfo.class);
        when(candidate.getId()).thenReturn(id);
        when(candidate.getIgHandle()).thenReturn(handle);
        return candidate;
    }

    private InstagramDiscoveryResult discoveryResult(Long sourceCreatorId, boolean created) {
        return new InstagramDiscoveryResult(
                sourceCreatorId,
                sourceCreatorId + 100,
                "username",
                created,
                100L,
                10L,
                new BigDecimal("1.25"),
                LocalDateTime.of(2026, 8, 14, 10, 0)
        );
    }
}
