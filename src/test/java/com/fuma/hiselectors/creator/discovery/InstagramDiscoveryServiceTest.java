package com.fuma.hiselectors.creator.discovery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fuma.hiselectors.creator.discovery.dto.InstagramBusinessDiscoveryResponse.BusinessDiscovery;
import com.fuma.hiselectors.creator.discovery.dto.InstagramBusinessDiscoveryResponse.Media;
import com.fuma.hiselectors.creator.discovery.dto.InstagramBusinessDiscoveryResponse.MediaItem;
import com.fuma.hiselectors.creator.discovery.dto.InstagramDiscoveryResult;
import com.fuma.hiselectors.creator.model.CreatorDiscoveryInfo;
import com.fuma.hiselectors.creator.model.CreatorPool;
import com.fuma.hiselectors.creator.repository.CreatorDiscoveryInfoRepository;
import com.fuma.hiselectors.creator.repository.CreatorPoolRepository;
import com.fuma.hiselectors.exception.BusinessException;
import com.fuma.hiselectors.exception.ErrorCode;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith(MockitoExtension.class)
class InstagramDiscoveryServiceTest {

    @Mock MetaGraphApiClient metaGraphApiClient;
    @Mock InstagramEngagementCalculator engagementCalculator;
    @Mock CreatorPoolRepository creatorPoolRepository;
    @Mock CreatorDiscoveryInfoRepository discoveryInfoRepository;
    @Mock TransactionTemplate transactionTemplate;

    @InjectMocks InstagramDiscoveryService service;

    @BeforeEach
    void setUp() {
        lenient().when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
            TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.doInTransaction(null);
        });
    }

    @Test
    void 신규_인스타그램_계정을_별도_크리에이터로_저장한다() {
        CreatorPool youtubeCreator = youtubeCreator("BEAUTY");
        CreatorDiscoveryInfo info = mock(CreatorDiscoveryInfo.class);
        CreatorPool savedInstagram = mock(CreatorPool.class);
        BusinessDiscovery discovered = discoveredAccount();

        when(creatorPoolRepository.findById(10L)).thenReturn(Optional.of(youtubeCreator));
        when(discoveryInfoRepository.findById(10L)).thenReturn(Optional.of(info));
        when(info.getIgHandle()).thenReturn("nike");
        when(metaGraphApiClient.discover("nike", 25)).thenReturn(discovered);
        when(engagementCalculator.calculate(291_530_362L, discovered.media()))
                .thenReturn(new BigDecimal("0.04"));
        when(creatorPoolRepository.findFirstBySnsCodeAndAccountIdOrderByIdAsc(
                "INSTAGRAM", "17841400602400210"))
                .thenReturn(Optional.empty());
        when(creatorPoolRepository.findFirstBySnsCodeAndAccountIdOrderByIdAsc(
                "INSTAGRAM", "nike"))
                .thenReturn(Optional.empty());
        when(creatorPoolRepository.saveAndFlush(any(CreatorPool.class)))
                .thenReturn(savedInstagram);
        when(savedInstagram.getId()).thenReturn(20L);

        InstagramDiscoveryResult result = service.discoverFromYoutubeCreator(10L);

        ArgumentCaptor<CreatorPool> captor = ArgumentCaptor.forClass(CreatorPool.class);
        verify(creatorPoolRepository).saveAndFlush(captor.capture());
        CreatorPool instagram = captor.getValue();
        assertThat(instagram.getSnsCode()).isEqualTo("INSTAGRAM");
        assertThat(instagram.getAccountId()).isEqualTo("17841400602400210");
        assertThat(instagram.getCreatorName()).isEqualTo("nike");
        assertThat(instagram.getCategory()).isEqualTo("BEAUTY");
        assertThat(instagram.getFollowerCount()).isEqualTo(291_530_362L);
        assertThat(instagram.getEngagementRate()).isEqualByComparingTo("0.04");
        assertThat(instagram.getLastContentAt())
                .isEqualTo(LocalDateTime.of(2026, 8, 12, 2, 0, 58));

        assertThat(result.instagramCreatorId()).isEqualTo(20L);
        assertThat(result.created()).isTrue();
        assertThat(result.mediaCount()).isEqualTo(1_668L);
        verify(discoveryInfoRepository).save(any(CreatorDiscoveryInfo.class));
    }

    @Test
    void 기존_인스타그램_계정은_지표를_갱신하고_복구한다() {
        CreatorPool youtubeCreator = youtubeCreator("BEAUTY");
        CreatorDiscoveryInfo info = mock(CreatorDiscoveryInfo.class);
        CreatorPool existing = mock(CreatorPool.class);
        BusinessDiscovery discovered = discoveredAccount();

        when(creatorPoolRepository.findById(10L)).thenReturn(Optional.of(youtubeCreator));
        when(discoveryInfoRepository.findById(10L)).thenReturn(Optional.of(info));
        when(info.getIgHandle()).thenReturn("nike");
        when(metaGraphApiClient.discover("nike", 25)).thenReturn(discovered);
        when(engagementCalculator.calculate(291_530_362L, discovered.media()))
                .thenReturn(new BigDecimal("0.04"));
        when(creatorPoolRepository.findFirstBySnsCodeAndAccountIdOrderByIdAsc(
                "INSTAGRAM", "17841400602400210"))
                .thenReturn(Optional.of(existing));
        when(existing.getId()).thenReturn(20L);
        when(existing.getAccountId()).thenReturn("17841400602400210");
        when(existing.isDeleted()).thenReturn(true);

        InstagramDiscoveryResult result = service.discoverFromYoutubeCreator(10L);

        verify(existing).updateProfile(
                "nike",
                291_530_362L,
                new BigDecimal("0.04"),
                LocalDateTime.of(2026, 8, 12, 2, 0, 58)
        );
        verify(existing).restore();
        verify(creatorPoolRepository, never()).saveAndFlush(any(CreatorPool.class));
        assertThat(result.created()).isFalse();
    }

    @Test
    void 동시_최초_저장_충돌이_나면_이미_생긴_계정을_갱신한다() {
        CreatorPool youtubeCreator = youtubeCreator("BEAUTY");
        CreatorDiscoveryInfo info = mock(CreatorDiscoveryInfo.class);
        CreatorPool winner = mock(CreatorPool.class);
        BusinessDiscovery discovered = discoveredAccount();

        when(creatorPoolRepository.findById(10L)).thenReturn(Optional.of(youtubeCreator));
        when(discoveryInfoRepository.findById(10L)).thenReturn(Optional.of(info));
        when(info.getIgHandle()).thenReturn("nike");
        when(metaGraphApiClient.discover("nike", 25)).thenReturn(discovered);
        when(engagementCalculator.calculate(291_530_362L, discovered.media()))
                .thenReturn(new BigDecimal("0.04"));
        when(creatorPoolRepository.findFirstBySnsCodeAndAccountIdOrderByIdAsc(
                "INSTAGRAM", "17841400602400210"))
                .thenReturn(Optional.empty(), Optional.of(winner));
        when(creatorPoolRepository.findFirstBySnsCodeAndAccountIdOrderByIdAsc(
                "INSTAGRAM", "nike"))
                .thenReturn(Optional.empty());
        when(creatorPoolRepository.saveAndFlush(any(CreatorPool.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate"));
        when(winner.getId()).thenReturn(20L);

        InstagramDiscoveryResult result = service.discoverFromYoutubeCreator(10L);

        verify(winner).updateProfile(
                "nike", 291_530_362L, new BigDecimal("0.04"),
                LocalDateTime.of(2026, 8, 12, 2, 0, 58));
        assertThat(result.created()).isFalse();
        assertThat(result.instagramCreatorId()).isEqualTo(20L);
    }

    @Test
    void 추출된_인스타그램_사용자명이_없으면_Meta를_호출하지_않는다() {
        CreatorPool youtubeCreator = youtubeCreator("BEAUTY");
        when(creatorPoolRepository.findById(10L)).thenReturn(Optional.of(youtubeCreator));
        when(discoveryInfoRepository.findById(10L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.discoverFromYoutubeCreator(10L))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.INSTAGRAM_HANDLE_NOT_FOUND);
        verifyNoInteractions(metaGraphApiClient);
    }

    private CreatorPool youtubeCreator(String category) {
        return CreatorPool.builder()
                .snsCode("YOUTUBE")
                .accountId("UC_SOURCE")
                .creatorName("원본 채널")
                .category(category)
                .build();
    }

    private BusinessDiscovery discoveredAccount() {
        MediaItem mediaItem = new MediaItem(
                "media-id", null, "VIDEO", null,
                "2026-08-11T17:00:58+0000", 36_307L, 358L
        );
        return new BusinessDiscovery(
                "17841400602400210", "nike", "Nike", "Just Do It.", null,
                291_530_362L, 1_668L, new Media(List.of(mediaItem))
        );
    }
}
