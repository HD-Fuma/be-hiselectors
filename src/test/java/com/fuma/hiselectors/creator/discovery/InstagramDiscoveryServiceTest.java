package com.fuma.hiselectors.creator.discovery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
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
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

@ExtendWith(MockitoExtension.class)
class InstagramDiscoveryServiceTest {

    @Mock MetaGraphApiClient metaGraphApiClient;
    @Mock InstagramEngagementCalculator engagementCalculator;
    @Mock PublicEmailExtractor publicEmailExtractor;
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
        lenient().when(publicEmailExtractor.extract(any()))
                .thenReturn(Optional.of("contact@nike.com"));
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
        assertThat(instagram.getEmail()).isEqualTo("contact@nike.com");
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
    void 공개_이메일이_없으면_기존_계정을_활성_풀에서_제외한다() {
        CreatorPool youtubeCreator = youtubeCreator("BEAUTY");
        CreatorDiscoveryInfo info = mock(CreatorDiscoveryInfo.class);
        CreatorPool existing = mock(CreatorPool.class);
        BusinessDiscovery discovered = new BusinessDiscovery(
                "17841400602400210", "nike", "Nike", "Just Do It.", null,
                291_530_362L, 1_668L, new Media(List.of()));

        when(creatorPoolRepository.findById(10L)).thenReturn(Optional.of(youtubeCreator));
        when(discoveryInfoRepository.findById(10L)).thenReturn(Optional.of(info));
        when(info.getIgHandle()).thenReturn("nike");
        when(metaGraphApiClient.discover("nike", 25)).thenReturn(discovered);
        when(publicEmailExtractor.extract("Just Do It.")).thenReturn(Optional.empty());
        when(publicEmailExtractor.extract((String) null)).thenReturn(Optional.empty());
        when(creatorPoolRepository.findFirstBySnsCodeAndAccountIdOrderByIdAsc(
                "INSTAGRAM", "17841400602400210")).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.discoverFromYoutubeCreator(10L))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.CREATOR_EMAIL_REQUIRED);

        verify(existing).softDelete();
        verifyNoInteractions(engagementCalculator);
    }

    @Test
    void 기존_인스타그램_계정은_지표를_갱신하고_복구한다() {
        CreatorPool youtubeCreator = CreatorPool.builder()
                .snsCode("YOUTUBE")
                .accountId("UC_SOURCE")
                .creatorName("원본 채널")
                .email("youtube@example.com")
                .category("BEAUTY")
                .build();
        CreatorDiscoveryInfo info = mock(CreatorDiscoveryInfo.class);
        CreatorPool existing = mock(CreatorPool.class);
        BusinessDiscovery discovered = discoveredAccount();

        when(creatorPoolRepository.findById(10L)).thenReturn(Optional.of(youtubeCreator));
        when(discoveryInfoRepository.findById(10L)).thenReturn(Optional.of(info));
        when(info.getIgHandle()).thenReturn("nike");
        when(metaGraphApiClient.discover("nike", 25)).thenReturn(discovered);
        when(publicEmailExtractor.extract(discovered.biography())).thenReturn(Optional.empty());
        when(publicEmailExtractor.extract("youtube@example.com"))
                .thenReturn(Optional.of("youtube@example.com"));
        when(engagementCalculator.calculate(291_530_362L, discovered.media()))
                .thenReturn(new BigDecimal("0.04"));
        when(creatorPoolRepository.findFirstBySnsCodeAndAccountIdOrderByIdAsc(
                "INSTAGRAM", "17841400602400210"))
                .thenReturn(Optional.of(existing));
        when(existing.getId()).thenReturn(20L);
        when(existing.getAccountId()).thenReturn("17841400602400210");
        when(existing.isDeleted()).thenReturn(true);

        InstagramDiscoveryResult result = service.discoverFromYoutubeCreator(10L);

        verify(existing).updateEmail("youtube@example.com");
        verify(existing).updateProfile(
                "nike",
                291_530_362L,
                new BigDecimal("0.04"),
                LocalDateTime.of(2026, 8, 12, 2, 0, 58)
        );
        verify(existing).restore();
        verify(creatorPoolRepository, never()).saveAndFlush(any(CreatorPool.class));
        verify(publicEmailExtractor).extract(discovered.biography());
        assertThat(result.created()).isFalse();
    }

    @Test
    void 최근_90일_경계에_따라_활동_수를_저장한다() {
        CreatorPool youtubeCreator = youtubeCreator("BEAUTY");
        CreatorDiscoveryInfo sourceInfo = mock(CreatorDiscoveryInfo.class);
        CreatorPool savedInstagram = mock(CreatorPool.class);
        ZoneId seoul = ZoneId.of("Asia/Seoul");
        BusinessDiscovery discovered = discoveredAccount(List.of(
                mediaItem("recent", OffsetDateTime.now(seoul).minusDays(89).toString()),
                mediaItem("old", OffsetDateTime.now(seoul).minusDays(91).toString())
        ));

        when(creatorPoolRepository.findById(10L)).thenReturn(Optional.of(youtubeCreator));
        when(discoveryInfoRepository.findById(10L)).thenReturn(Optional.of(sourceInfo));
        when(sourceInfo.getIgHandle()).thenReturn("nike");
        when(metaGraphApiClient.discover("nike", 25)).thenReturn(discovered);
        when(engagementCalculator.calculate(291_530_362L, discovered.media()))
                .thenReturn(new BigDecimal("0.04"));
        when(creatorPoolRepository.findFirstBySnsCodeAndAccountIdOrderByIdAsc(
                "INSTAGRAM", "17841400602400210")).thenReturn(Optional.empty());
        when(creatorPoolRepository.findFirstBySnsCodeAndAccountIdOrderByIdAsc(
                "INSTAGRAM", "nike")).thenReturn(Optional.empty());
        when(creatorPoolRepository.saveAndFlush(any(CreatorPool.class)))
                .thenReturn(savedInstagram);
        when(savedInstagram.getId()).thenReturn(20L);

        service.discoverFromYoutubeCreator(10L);

        ArgumentCaptor<CreatorDiscoveryInfo> captor =
                ArgumentCaptor.forClass(CreatorDiscoveryInfo.class);
        verify(discoveryInfoRepository).save(captor.capture());
        assertThat(captor.getValue().getRecent90DayContentCount()).isEqualTo(1);
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

        verify(winner).updateEmail("contact@nike.com");
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
        return discoveredAccount(List.of(mediaItem));
    }

    private BusinessDiscovery discoveredAccount(List<MediaItem> mediaItems) {
        return new BusinessDiscovery(
                "17841400602400210", "nike", "Nike", "Just Do It. contact@nike.com", null,
                291_530_362L, 1_668L, new Media(mediaItems)
        );
    }

    private MediaItem mediaItem(String id, String timestamp) {
        return new MediaItem(id, null, "VIDEO", null, timestamp, 1L, 1L);
    }
}
