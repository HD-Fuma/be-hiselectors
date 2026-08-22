package com.fuma.hiselectors.creator.discovery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fuma.hiselectors.category.model.Category;
import com.fuma.hiselectors.category.model.DiscoveryKeyword;
import com.fuma.hiselectors.category.repository.DiscoveryKeywordRepository;
import com.fuma.hiselectors.creator.discovery.BrandScoreCalculator.BrandScore;
import com.fuma.hiselectors.creator.discovery.IgHandleExtractor.IgHandle;
import com.fuma.hiselectors.creator.discovery.YoutubeDiscoveryClient.DiscoveredChannel;
import com.fuma.hiselectors.creator.discovery.dto.DiscoveryRunResult;
import com.fuma.hiselectors.creator.model.CreatorDiscoveryInfo;
import com.fuma.hiselectors.creator.model.CreatorDiscoverySource;
import com.fuma.hiselectors.creator.model.CreatorPool;
import com.fuma.hiselectors.creator.repository.CreatorDiscoveryInfoRepository;
import com.fuma.hiselectors.creator.repository.CreatorDiscoverySourceRepository;
import com.fuma.hiselectors.creator.repository.CreatorPoolRepository;
import com.fuma.hiselectors.creator.service.CreatorDiscoveryService;
import com.fuma.hiselectors.exception.BusinessException;
import com.fuma.hiselectors.exception.ErrorCode;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

@ExtendWith(MockitoExtension.class)
class DiscoveryPipelineServiceTest {

    @Mock
    private YoutubeDiscoveryClient youtubeClient;
    @Mock
    private IgHandleExtractor igHandleExtractor;
    @Mock
    private PublicEmailExtractor publicEmailExtractor;
    @Mock
    private BrandScoreCalculator brandScoreCalculator;
    @Mock
    private DiscoveryKeywordRepository keywordRepository;
    @Mock
    private CreatorPoolRepository creatorPoolRepository;
    @Mock
    private CreatorDiscoveryInfoRepository discoveryInfoRepository;
    @Mock
    private CreatorDiscoverySourceRepository discoverySourceRepository;
    @Mock
    private CreatorDiscoveryService creatorDiscoveryService;
    @Mock
    private TransactionTemplate transactionTemplate;

    @InjectMocks
    private DiscoveryPipelineService discoveryPipelineService;

    private DiscoveryKeyword keyword;

    @BeforeEach
    void setUp() {
        lenient().when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
            TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.doInTransaction(null);
        });

        Category category = Category.builder()
                .code("BEAUTY")
                .name("뷰티")
                .build();
        keyword = category.addKeyword("겟레디윗미", 10);
    }

    @Test
    @DisplayName("존재하지 않는 키워드로 발굴을 실행할 수 없다")
    void missingKeyword() {
        when(keywordRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> discoveryPipelineService.runByKeyword(999L, 25))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.KEYWORD_NOT_FOUND);
        verifyNoInteractions(youtubeClient, creatorPoolRepository);
    }

    @Test
    @DisplayName("검색 결과가 없어도 정상 종료하고 키워드 실행 시각을 갱신한다")
    void emptyDiscoveryResult() {
        when(keywordRepository.findById(1L)).thenReturn(Optional.of(keyword));
        when(youtubeClient.discoverByKeyword("겟레디윗미", 25)).thenReturn(List.of());
        when(youtubeClient.consumedQuota()).thenReturn(100);

        DiscoveryRunResult result = discoveryPipelineService.runByKeyword(1L, 25);

        assertThat(result.keyword()).isEqualTo("겟레디윗미");
        assertThat(result.categoryCode()).isEqualTo("BEAUTY");
        assertThat(result.discovered()).isZero();
        assertThat(result.created()).isZero();
        assertThat(result.updated()).isZero();
        assertThat(result.consumedQuota()).isEqualTo(100);
        assertThat(keyword.getLastRunAt()).isNotNull();
        verifyNoInteractions(creatorPoolRepository, discoveryInfoRepository,
                discoverySourceRepository, creatorDiscoveryService);
    }

    @Test
    @DisplayName("신규 YouTube 채널과 발굴 정보 및 출처를 저장한다")
    void saveNewCreator() {
        LocalDateTime uploadedAt = LocalDateTime.of(2026, 8, 1, 12, 0);
        DiscoveredChannel channel = new DiscoveredChannel(
                "UC_NEW", "새 크리에이터", "Instagram @new_creator / hello@example.com",
                120_000L, 3_000_000L, uploadedAt,
                12, 1_000L, 40L, 10L);
        CreatorPool savedCreator = org.mockito.Mockito.mock(CreatorPool.class);

        when(keywordRepository.findById(1L)).thenReturn(Optional.of(keyword));
        when(youtubeClient.discoverByKeyword("겟레디윗미", 25))
                .thenReturn(List.of(channel));
        when(youtubeClient.consumedQuota()).thenReturn(102);
        when(igHandleExtractor.extract(channel.description()))
                .thenReturn(Optional.of(new IgHandle("new_creator", IgHandleSource.LABELED)));
        when(publicEmailExtractor.extract(channel.description()))
                .thenReturn(Optional.of("hello@example.com"));
        when(brandScoreCalculator.calculate(
                channel.title(), channel.description(), "new_creator"))
                .thenReturn(new BrandScore(0, List.of()));
        when(creatorPoolRepository.findFirstBySnsCodeAndAccountIdOrderByIdAsc(
                "YOUTUBE", "UC_NEW"))
                .thenReturn(Optional.empty());
        when(creatorPoolRepository.save(any(CreatorPool.class))).thenReturn(savedCreator);
        when(savedCreator.getId()).thenReturn(101L);
        when(discoveryInfoRepository.findById(101L)).thenReturn(Optional.empty());
        when(discoverySourceRepository.findByCreatorPoolIdAndKeywordId(101L, null))
                .thenReturn(Optional.empty());

        DiscoveryRunResult result = discoveryPipelineService.runByKeyword(1L, 25);

        assertThat(result.discovered()).isEqualTo(1);
        assertThat(result.created()).isEqualTo(1);
        assertThat(result.updated()).isZero();
        assertThat(result.consumedQuota()).isEqualTo(102);

        ArgumentCaptor<CreatorPool> creatorCaptor = ArgumentCaptor.forClass(CreatorPool.class);
        verify(creatorPoolRepository).save(creatorCaptor.capture());
        CreatorPool creator = creatorCaptor.getValue();
        assertThat(creator.getSnsCode()).isEqualTo("YOUTUBE");
        assertThat(creator.getAccountId()).isEqualTo("UC_NEW");
        assertThat(creator.getEmail()).isEqualTo("hello@example.com");
        assertThat(creator.getFollowerCount()).isEqualTo(120_000L);
        assertThat(creator.getEngagementRate()).isEqualByComparingTo("5.00");
        assertThat(creator.getCategory()).isEqualTo("BEAUTY");

        ArgumentCaptor<CreatorDiscoveryInfo> infoCaptor =
                ArgumentCaptor.forClass(CreatorDiscoveryInfo.class);
        verify(discoveryInfoRepository).save(infoCaptor.capture());
        assertThat(infoCaptor.getValue().getIgHandle()).isEqualTo("new_creator");
        assertThat(infoCaptor.getValue().getIgConfidence()).isEqualByComparingTo("0.75");
        assertThat(infoCaptor.getValue().getRecent90DayContentCount()).isEqualTo(12);
        assertThat(infoCaptor.getValue().getDiscoveredAt()).isNotNull();

        ArgumentCaptor<CreatorDiscoverySource> sourceCaptor =
                ArgumentCaptor.forClass(CreatorDiscoverySource.class);
        verify(discoverySourceRepository).save(sourceCaptor.capture());
        assertThat(sourceCaptor.getValue().getViewShare()).isEqualByComparingTo("1.00000");
        assertThat(sourceCaptor.getValue().getDiscoveredAt()).isNotNull();
        verify(creatorDiscoveryService).refreshRepresentativeCategory(101L);
    }

    @Test
    @DisplayName("기존 채널은 새로 만들지 않고 지표와 발굴 이력을 갱신한다")
    void updateExistingCreator() {
        LocalDateTime uploadedAt = LocalDateTime.of(2026, 8, 2, 12, 0);
        DiscoveredChannel channel = new DiscoveredChannel(
                "UC_EXISTING", "기존 크리에이터", "contact@example.com",
                50_000L, 1_000_000L, uploadedAt,
                7, 200L, 8L, 2L);
        CreatorPool existingCreator = org.mockito.Mockito.mock(CreatorPool.class);
        CreatorDiscoveryInfo existingInfo = org.mockito.Mockito.mock(CreatorDiscoveryInfo.class);
        CreatorDiscoverySource existingSource =
                org.mockito.Mockito.mock(CreatorDiscoverySource.class);

        when(keywordRepository.findById(1L)).thenReturn(Optional.of(keyword));
        when(youtubeClient.discoverByKeyword("겟레디윗미", 25))
                .thenReturn(List.of(channel));
        when(igHandleExtractor.extract("contact@example.com")).thenReturn(Optional.empty());
        when(brandScoreCalculator.calculate(
                "기존 크리에이터", "contact@example.com", null))
                .thenReturn(new BrandScore(2, List.of("공식")));
        when(creatorPoolRepository.findFirstBySnsCodeAndAccountIdOrderByIdAsc(
                "YOUTUBE", "UC_EXISTING"))
                .thenReturn(Optional.of(existingCreator));
        when(existingCreator.getId()).thenReturn(102L);
        when(existingCreator.isDeleted()).thenReturn(true);
        when(discoveryInfoRepository.findById(102L)).thenReturn(Optional.of(existingInfo));
        when(discoverySourceRepository.findByCreatorPoolIdAndKeywordId(102L, null))
                .thenReturn(Optional.of(existingSource));

        DiscoveryRunResult result = discoveryPipelineService.runByKeyword(1L, 25);

        assertThat(result.created()).isZero();
        assertThat(result.updated()).isEqualTo(1);
        verify(existingCreator).updateMetrics(
                50_000L, new BigDecimal("5.00"), uploadedAt);
        verify(existingCreator).restore();
        verify(existingInfo).refresh(2, "공식", null, null);
        verify(existingInfo).updateRecent90DayContentCount(7);
        verify(existingSource).refresh(new BigDecimal("1.00000"));
        verify(creatorPoolRepository, never()).save(any(CreatorPool.class));
        verifyNoInteractions(publicEmailExtractor);
        verify(creatorDiscoveryService).refreshRepresentativeCategory(102L);
    }
}
