package com.fuma.hiselectors.selectors.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fuma.hiselectors.application.model.SnsPlatform;
import com.fuma.hiselectors.application.service.LocalAnalyzerClient;
import com.fuma.hiselectors.application.service.LocalAnalyzerClient.LocalAnalysis;
import com.fuma.hiselectors.content.client.ContentFetcher;
import com.fuma.hiselectors.content.client.dto.RawContent;
import com.fuma.hiselectors.content.model.ContentType;
import com.fuma.hiselectors.exception.BusinessException;
import com.fuma.hiselectors.exception.ErrorCode;
import com.fuma.hiselectors.selectors.dto.SelectorSnsEnrichmentResponse;
import com.fuma.hiselectors.selectors.model.Selectors;
import com.fuma.hiselectors.selectors.model.SelectorsSnsAccount;
import com.fuma.hiselectors.selectors.repository.SelectorsRepository;
import com.fuma.hiselectors.selectors.repository.SelectorsSnsAccountRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

@ExtendWith(MockitoExtension.class)
class SelectorSnsEnrichmentServiceTest {

    private static final Long SELECTORS_ID = 30L;
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-08-31T03:00:00Z"), SEOUL);
    private static final LocalDateTime NOW = LocalDateTime.now(CLOCK);

    @Mock
    private SelectorsRepository selectorsRepository;
    @Mock
    private SelectorsSnsAccountRepository selectorsSnsAccountRepository;
    @Mock
    private ContentFetcher youtubeFetcher;
    @Mock
    private ContentFetcher instagramFetcher;
    @Mock
    private LocalAnalyzerClient analyzer;
    @Mock
    private TransactionTemplate transactionTemplate;

    private SelectorSnsEnrichmentService service;

    @BeforeEach
    void setUp() {
        lenient().when(youtubeFetcher.supports()).thenReturn(SnsPlatform.YOUTUBE);
        lenient().when(instagramFetcher.supports()).thenReturn(SnsPlatform.INSTAGRAM);
        lenient().doAnswer(invocation -> {
            Consumer<TransactionStatus> callback = invocation.getArgument(0);
            callback.accept(null);
            return null;
        }).when(transactionTemplate).executeWithoutResult(any());
        service = new SelectorSnsEnrichmentService(
                selectorsRepository,
                selectorsSnsAccountRepository,
                List.of(youtubeFetcher, instagramFetcher),
                analyzer,
                transactionTemplate,
                CLOCK);
    }

    @Test
    void fillsMissingYoutubeProfileAndCategoryFromRecentTitles() {
        Selectors selectors = selectors();
        SelectorsSnsAccount account = account(SnsPlatform.YOUTUBE, "@mama", null);
        stubSelector(selectors, account);
        when(youtubeFetcher.fetchProfile("@mama"))
                .thenReturn(new ContentFetcher.Profile(
                        "https://cdn.example.com/mama.jpg", 152_000L, 180L));
        when(youtubeFetcher.fetchByAccount("@mama", NOW.minusDays(90)))
                .thenReturn(List.of(
                        youtube("v1", "김치찌개 레시피", NOW.minusDays(1), 1_000L),
                        youtube("v2", "된장찌개 맛집", NOW.minusDays(2), 800L),
                        youtube("old", "여행 브이로그", NOW.minusDays(91), 9_000L)));
        when(youtubeFetcher.addStatistics(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(analyzer.analyze(any())).thenReturn(food());

        SelectorSnsEnrichmentResponse result = service.enrich(SELECTORS_ID, false);

        assertThat(result.profileImageUpdated()).isTrue();
        assertThat(result.profileImageUrl()).isEqualTo("https://cdn.example.com/mama.jpg");
        assertThat(result.categoryUpdated()).isTrue();
        assertThat(result.category()).isEqualTo("FOOD");
        assertThat(account.getProfileImageUrl()).isEqualTo("https://cdn.example.com/mama.jpg");
        assertThat(account.getFollowerCount()).isEqualTo(152_000L);
        assertThat(selectors.getCategory()).isEqualTo("FOOD");
        verify(analyzer).analyze("김치찌개 레시피");
        verify(analyzer).analyze("된장찌개 맛집");
        verify(analyzer, never()).analyze("여행 브이로그");
    }

    @Test
    void skipsFilledValuesUnlessForced() {
        Selectors selectors = selectors();
        selectors.assignCategory("BEAUTY");
        SelectorsSnsAccount account = account(
                SnsPlatform.INSTAGRAM, "gmcoo.k", "https://cdn.example.com/old.jpg");
        when(selectorsRepository.findByIdAndDeletedFalse(SELECTORS_ID))
                .thenReturn(Optional.of(selectors));
        when(selectorsSnsAccountRepository.findBySelectorsIdAndDeletedFalse(SELECTORS_ID))
                .thenReturn(Optional.of(account));

        SelectorSnsEnrichmentResponse result = service.enrich(SELECTORS_ID, false);

        assertThat(result.profileImageUpdated()).isFalse();
        assertThat(result.categoryUpdated()).isFalse();
        assertThat(result.profileSkipReason()).contains("이미 프로필");
        assertThat(result.categorySkipReason()).contains("이미 카테고리");
        verify(instagramFetcher, never()).fetchProfile(any());
        verify(analyzer, never()).analyze(any());
    }

    @Test
    void forceOverwritesExistingProfileAndCategory() {
        Selectors selectors = selectors();
        selectors.assignCategory("BEAUTY");
        SelectorsSnsAccount account = account(
                SnsPlatform.INSTAGRAM, "gmcoo.k", "https://cdn.example.com/old.jpg");
        stubSelector(selectors, account);
        when(instagramFetcher.fetchProfile("gmcoo.k"))
                .thenReturn(new ContentFetcher.Profile(
                        "https://cdn.example.com/new.jpg", 32_000L, 450L));
        when(instagramFetcher.fetchByAccount(eq("gmcoo.k"), eq(LocalDateTime.MIN), eq(10)))
                .thenReturn(List.of(instagram("p1", "오늘의 오피스룩", NOW.minusHours(3))));
        when(instagramFetcher.addStatistics(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(analyzer.analyze("오늘의 오피스룩")).thenReturn(fashion());

        SelectorSnsEnrichmentResponse result = service.enrich(SELECTORS_ID, true);

        assertThat(result.profileImageUpdated()).isTrue();
        assertThat(result.categoryUpdated()).isTrue();
        assertThat(account.getProfileImageUrl()).isEqualTo("https://cdn.example.com/new.jpg");
        assertThat(selectors.getCategory()).isEqualTo("FASHION");
    }

    @Test
    void usesCategoryModeFromRecentContents() {
        Selectors selectors = selectors();
        SelectorsSnsAccount account = account(SnsPlatform.YOUTUBE, "@mama", "https://cdn.example.com/a.jpg");
        stubSelector(selectors, account);
        when(youtubeFetcher.fetchByAccount("@mama", NOW.minusDays(90)))
                .thenReturn(List.of(
                        youtube("v1", "립틴트 리뷰", NOW.minusDays(1), 100L),
                        youtube("v2", "파운데이션 추천", NOW.minusDays(2), 90L),
                        youtube("v3", "김치찌개", NOW.minusDays(3), 80L)));
        when(youtubeFetcher.addStatistics(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(analyzer.analyze("립틴트 리뷰")).thenReturn(beauty());
        when(analyzer.analyze("파운데이션 추천")).thenReturn(beauty());
        when(analyzer.analyze("김치찌개")).thenReturn(food());

        SelectorSnsEnrichmentResponse result = service.enrich(SELECTORS_ID, false);

        assertThat(result.profileImageUpdated()).isFalse();
        assertThat(result.category()).isEqualTo("BEAUTY");
        verify(youtubeFetcher, never()).fetchProfile(any());
    }

    @Test
    void persistsProfileThenRethrowsWhenAnalyzerIsDown() {
        Selectors selectors = selectors();
        SelectorsSnsAccount account = account(SnsPlatform.YOUTUBE, "@mama", null);
        stubSelector(selectors, account);
        when(youtubeFetcher.fetchProfile("@mama"))
                .thenReturn(new ContentFetcher.Profile(
                        "https://cdn.example.com/mama.jpg", 1L, 1L));
        when(youtubeFetcher.fetchByAccount("@mama", NOW.minusDays(90)))
                .thenReturn(List.of(youtube("v1", "레시피", NOW.minusDays(1), 10L)));
        when(youtubeFetcher.addStatistics(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(analyzer.analyze("레시피"))
                .thenThrow(new BusinessException(ErrorCode.ANALYZER_UNAVAILABLE));

        assertThatThrownBy(() -> service.enrich(SELECTORS_ID, false))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).getErrorCode())
                .isEqualTo(ErrorCode.ANALYZER_UNAVAILABLE);
        assertThat(account.getProfileImageUrl()).isEqualTo("https://cdn.example.com/mama.jpg");
    }

    @Test
    void throwsWhenSelectorsIsMissing() {
        when(selectorsRepository.findByIdAndDeletedFalse(SELECTORS_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.enrich(SELECTORS_ID, false))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).getErrorCode())
                .isEqualTo(ErrorCode.SELECTOR_NOT_FOUND);
    }

    @Test
    void throwsWhenSnsAccountIsMissing() {
        when(selectorsRepository.findByIdAndDeletedFalse(SELECTORS_ID))
                .thenReturn(Optional.of(selectors()));
        when(selectorsSnsAccountRepository.findBySelectorsIdAndDeletedFalse(SELECTORS_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.enrich(SELECTORS_ID, false))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).getErrorCode())
                .isEqualTo(ErrorCode.SELECTOR_SNS_ACCOUNT_NOT_FOUND);
    }

    @Test
    void batchContinuesAfterOneFailure() {
        Selectors first = selectors();
        Selectors second = selectors();
        ReflectionTestUtils.setField(second, "id", 31L);
        when(selectorsRepository.findSnsEnrichmentTargets(eq(false), any(Pageable.class)))
                .thenReturn(List.of(first, second));
        when(selectorsRepository.findByIdAndDeletedFalse(30L)).thenReturn(Optional.empty());
        SelectorsSnsAccount secondAccount = account(SnsPlatform.YOUTUBE, "@second", null);
        when(selectorsRepository.findByIdAndDeletedFalse(31L)).thenReturn(Optional.of(second));
        when(selectorsSnsAccountRepository.findBySelectorsIdAndDeletedFalse(31L))
                .thenReturn(Optional.of(secondAccount));
        when(selectorsRepository.findByIdForUpdate(31L)).thenReturn(Optional.of(second));
        when(selectorsSnsAccountRepository.findBySelectorsIdAndDeletedFalseForUpdate(31L))
                .thenReturn(Optional.of(secondAccount));
        when(youtubeFetcher.fetchProfile("@second"))
                .thenReturn(new ContentFetcher.Profile("https://cdn.example.com/second.jpg", 1L, 1L));
        when(youtubeFetcher.fetchByAccount("@second", NOW.minusDays(90))).thenReturn(List.of());
        when(youtubeFetcher.addStatistics(any())).thenReturn(List.of());

        SelectorSnsEnrichmentResponse.Batch batch = service.enrichMissing(false, 5);

        assertThat(batch.targetCount()).isEqualTo(2);
        assertThat(batch.failedCount()).isEqualTo(1);
        assertThat(batch.profileImageUpdatedCount()).isEqualTo(1);
        assertThat(batch.results()).extracting(SelectorSnsEnrichmentResponse::selectorsId)
                .containsExactly(30L, 31L);
        verify(youtubeFetcher).fetchProfile("@second");
    }

    @Test
    void batchSizeIsCappedAtFifty() {
        when(selectorsRepository.findSnsEnrichmentTargets(eq(true), any(Pageable.class)))
                .thenReturn(List.of());

        service.enrichMissing(true, 200);

        org.mockito.ArgumentCaptor<Pageable> captor =
                org.mockito.ArgumentCaptor.forClass(Pageable.class);
        verify(selectorsRepository).findSnsEnrichmentTargets(eq(true), captor.capture());
        assertThat(captor.getValue().getPageSize()).isEqualTo(50);
    }

    private void stubSelector(Selectors selectors, SelectorsSnsAccount account) {
        when(selectorsRepository.findByIdAndDeletedFalse(SELECTORS_ID))
                .thenReturn(Optional.of(selectors));
        when(selectorsSnsAccountRepository.findBySelectorsIdAndDeletedFalse(SELECTORS_ID))
                .thenReturn(Optional.of(account));
        when(selectorsRepository.findByIdForUpdate(SELECTORS_ID)).thenReturn(Optional.of(selectors));
        when(selectorsSnsAccountRepository.findBySelectorsIdAndDeletedFalseForUpdate(SELECTORS_ID))
                .thenReturn(Optional.of(account));
    }

    private Selectors selectors() {
        Selectors selectors = Selectors.builder()
                .userId(10L)
                .selectorsRoleId(Selectors.ACTIVE_ROLE)
                .selectorsNickname("마마")
                .build();
        ReflectionTestUtils.setField(selectors, "id", SELECTORS_ID);
        return selectors;
    }

    private SelectorsSnsAccount account(SnsPlatform platform, String accountId, String imageUrl) {
        return SelectorsSnsAccount.builder()
                .selectorsId(SELECTORS_ID)
                .snsCode(platform)
                .accountId(accountId)
                .profileImageUrl(imageUrl)
                .build();
    }

    private RawContent youtube(String id, String title, LocalDateTime createdAt, Long views) {
        return new RawContent(
                SnsPlatform.YOUTUBE, id, "https://youtube.com/" + id, ContentType.SHORTS,
                List.of(title), createdAt, List.of())
                .withMetrics(views, 1L, 0L);
    }

    private RawContent instagram(String id, String caption, LocalDateTime createdAt) {
        return new RawContent(
                SnsPlatform.INSTAGRAM, id, "https://instagram.com/" + id, ContentType.FEED,
                caption, createdAt, List.of());
    }

    private LocalAnalysis food() {
        return new LocalAnalysis(List.of("레시피"), new LocalAnalysis.Category("FOOD", false));
    }

    private LocalAnalysis beauty() {
        return new LocalAnalysis(List.of("메이크업"), new LocalAnalysis.Category("BEAUTY", false));
    }

    private LocalAnalysis fashion() {
        return new LocalAnalysis(List.of("오피스룩"), new LocalAnalysis.Category("FASHION", false));
    }
}
