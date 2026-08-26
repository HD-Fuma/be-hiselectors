package com.fuma.hiselectors.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fuma.hiselectors.application.model.Application;
import com.fuma.hiselectors.application.model.ApplicationMedia;
import com.fuma.hiselectors.application.model.ApplicationStatus;
import com.fuma.hiselectors.application.model.SnsPlatform;
import com.fuma.hiselectors.application.repository.ApplicationMediaRepository;
import com.fuma.hiselectors.application.repository.ApplicationReportRepository;
import com.fuma.hiselectors.application.repository.ApplicationRepository;
import com.fuma.hiselectors.content.client.YoutubeContentFetcher;
import com.fuma.hiselectors.content.model.ContentType;
import com.fuma.hiselectors.content.model.MediaType;
import com.fuma.hiselectors.generation.model.Generation;
import com.fuma.hiselectors.generation.repository.GenerationRepository;
import com.fuma.hiselectors.user.model.User;
import com.fuma.hiselectors.user.repository.UserRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

class ApplicationAdminServiceTest {

    private static final LocalDateTime COLLECTED_AT =
            LocalDateTime.of(2026, 8, 20, 12, 0);

    private final ApplicationRepository applicationRepository =
            mock(ApplicationRepository.class);
    private final ApplicationMediaRepository mediaRepository =
            mock(ApplicationMediaRepository.class);
    private final ApplicationReportRepository reportRepository =
            mock(ApplicationReportRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final GenerationRepository generationRepository = mock(GenerationRepository.class);
    private final YoutubeContentFetcher youtubeContentFetcher = mock(YoutubeContentFetcher.class);
    private final ApplicationAdminService service = new ApplicationAdminService(
            applicationRepository, mediaRepository, reportRepository, userRepository,
            generationRepository, youtubeContentFetcher);

    private Application application;
    private User user;
    private Generation generation;

    @BeforeEach
    void setUp() {
        application = Application.builder()
                .userId(10L)
                .generationId(20L)
                .snsCode(SnsPlatform.INSTAGRAM)
                .snsAccountId("creator.handle")
                .profileUrl("https://www.instagram.com/creator.handle/")
                .followerCount(1_000L)
                .contentCount(500L)
                .lastContentAt(COLLECTED_AT.minusHours(1))
                .engagementRate(new BigDecimal("99.99"))
                .alarmYn(true)
                .policyAgreedAt(COLLECTED_AT.minusDays(30))
                .status(ApplicationStatus.PENDING)
                .build();
        application.updateProfileImageUrl("https://cdn.example.com/profile.jpg");
        ReflectionTestUtils.setField(application, "id", 1L);
        ReflectionTestUtils.setField(application, "createdAt", COLLECTED_AT.minusDays(30));
        ReflectionTestUtils.setField(application, "updatedAt", COLLECTED_AT);

        user = User.builder()
                .hiId("hi-user")
                .name("김지안")
                .email("jian@example.com")
                .phone("01012345678")
                .build();
        ReflectionTestUtils.setField(user, "id", 10L);

        generation = Generation.builder().generationName("2기").build();
        ReflectionTestUtils.setField(generation, "id", 20L);
    }

    @Test
    void searchMapsCollectedCountsAndMeasuredEngagementRate() {
        application.completeMediaCollection(COLLECTED_AT, new BigDecimal("15.00"));
        var pageable = PageRequest.of(0, 20);
        List<ApplicationMedia> contents = List.of(
                media("post-1", 0, 0, COLLECTED_AT.minusDays(3),
                        ContentType.POST, 100L, 10L, 5L),
                media("post-1", 0, 1, COLLECTED_AT.minusDays(3),
                        ContentType.POST, 100L, 10L, 5L),
                media("post-2", 1, 0, COLLECTED_AT.minusDays(1),
                        ContentType.REELS, null, 20L, null));
        when(applicationRepository.searchAdmin(
                "김지안", SnsPlatform.INSTAGRAM, ApplicationStatus.PENDING,
                20L, null, true, pageable))
                .thenReturn(new PageImpl<>(List.of(application), pageable, 1));
        when(userRepository.findAllById(List.of(10L))).thenReturn(List.of(user));
        when(generationRepository.findAllById(List.of(20L))).thenReturn(List.of(generation));
        when(mediaRepository
                .findAllByApplicationIdInOrderByApplicationIdAscSequenceNoAscMediaSequenceNoAsc(
                        List.of(1L)))
                .thenReturn(contents);

        var result = service.search(
                "  김지안  ", SnsPlatform.INSTAGRAM, ApplicationStatus.PENDING,
                20L, null, true, pageable);

        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent().getFirst()).satisfies(summary -> {
            assertThat(summary.applicantName()).isEqualTo("김지안");
            assertThat(summary.totalContentCount()).isEqualTo(500L);
            assertThat(summary.recent90DayContentCount()).isEqualTo(2L);
            assertThat(summary.engagementRate()).isEqualByComparingTo("15.00");
            assertThat(summary.profileUrl())
                    .isEqualTo("https://www.instagram.com/creator.handle/");
            assertThat(summary.profileImageUrl())
                    .isEqualTo("https://cdn.example.com/profile.jpg");
            assertThat(summary.lastPublishedAt())
                    .isEqualTo(COLLECTED_AT.minusDays(1));
            assertThat(summary.mediaCollectedAt()).isEqualTo(COLLECTED_AT);
        });
        verify(applicationRepository).searchAdmin(
                "김지안", SnsPlatform.INSTAGRAM, ApplicationStatus.PENDING,
                20L, null, true, pageable);
    }

    @Test
    void searchShowsYoutubeTitleWithoutReplacingStoredChannelId() {
        String channelId = "UC0000000000000000000000";
        ReflectionTestUtils.setField(application, "snsCode", SnsPlatform.YOUTUBE);
        ReflectionTestUtils.setField(application, "snsAccountId", channelId);
        var pageable = PageRequest.of(0, 20);
        when(applicationRepository.searchAdmin(
                null, SnsPlatform.YOUTUBE, null, null, null, null, pageable))
                .thenReturn(new PageImpl<>(List.of(application), pageable, 1));
        when(userRepository.findAllById(List.of(10L))).thenReturn(List.of(user));
        when(generationRepository.findAllById(List.of(20L))).thenReturn(List.of(generation));
        when(mediaRepository
                .findAllByApplicationIdInOrderByApplicationIdAscSequenceNoAscMediaSequenceNoAsc(
                        List.of(1L)))
                .thenReturn(List.of());
        when(youtubeContentFetcher.fetchChannelTitles(List.of(channelId)))
                .thenReturn(Map.of(channelId, "지안의 생활연구소"));

        var summary = service.search(
                null, SnsPlatform.YOUTUBE, null, null, null, null, pageable)
                .getContent().getFirst();

        assertThat(summary.snsAccountId()).isEqualTo(channelId);
        assertThat(summary.snsDisplayName()).isEqualTo("지안의 생활연구소");
        assertThat(application.getSnsAccountId()).isEqualTo(channelId);

        when(youtubeContentFetcher.fetchChannelTitles(List.of(channelId))).thenReturn(Map.of());
        assertThat(service.search(null, SnsPlatform.YOUTUBE, null, null, null, null, pageable)
                .getContent().getFirst().snsDisplayName()).isEqualTo(channelId);
    }

    @Test
    void searchForwardsOmittedMinimumCriteriaAsNull() {
        var pageable = PageRequest.of(0, 20);
        when(applicationRepository.searchAdmin(
                null, null, null, null, null, null, pageable))
                .thenReturn(new PageImpl<>(List.of(), pageable, 0));

        var result = service.search(null, null, null, null, null, null, pageable);

        assertThat(result).isEmpty();
        verify(applicationRepository).searchAdmin(
                null, null, null, null, null, null, pageable);
    }

    @Test
    void detailCalculatesMeasuredOnlyAveragesCadenceFormatsAndEngagement() {
        application.completeMediaCollection(COLLECTED_AT, new BigDecimal("15.00"));
        List<ApplicationMedia> contents = List.of(
                media("post-1", 0, 0, LocalDateTime.of(2026, 8, 1, 10, 0),
                        ContentType.POST, 100L, 10L, 5L),
                media("post-1", 0, 1, LocalDateTime.of(2026, 8, 1, 10, 0),
                        ContentType.POST, 100L, 10L, 5L),
                media("post-2", 1, 0, LocalDateTime.of(2026, 8, 11, 10, 0),
                        ContentType.REELS, null, 20L, null),
                media("post-3", 2, 0, LocalDateTime.of(2026, 8, 20, 10, 0),
                        null, 300L, null, 7L),
                media("old", 3, 0, LocalDateTime.of(2026, 5, 1, 10, 0),
                        ContentType.POST, 999L, 999L, 999L));
        when(applicationRepository.findById(1L)).thenReturn(Optional.of(application));
        when(userRepository.findById(10L)).thenReturn(Optional.of(user));
        when(generationRepository.findById(20L)).thenReturn(Optional.of(generation));
        when(mediaRepository.findAllByApplicationIdOrderBySequenceNoAscMediaSequenceNoAsc(1L))
                .thenReturn(contents);

        var result = service.findDetail(1L);
        var metrics = result.metrics();

        assertThat(metrics.analysisWindowDays()).isEqualTo(90);
        assertThat(metrics.totalContentCount()).isEqualTo(500L);
        assertThat(metrics.recent90DayContentCount()).isEqualTo(3L);
        assertThat(metrics.lastPublishedAt())
                .isEqualTo(LocalDateTime.of(2026, 8, 20, 10, 0));
        assertThat(metrics.uploadCadence().sampleCount()).isEqualTo(3);
        assertThat(metrics.uploadCadence().dailyAverage()).isEqualByComparingTo("0.03");
        assertThat(metrics.uploadCadence().weeklyAverage()).isEqualByComparingTo("0.23");
        assertThat(metrics.uploadCadence().maximumGapDays()).isEqualTo(10L);
        assertThat(metrics.averageViewCount().value()).isEqualByComparingTo("200.00");
        assertThat(metrics.averageViewCount().sampleCount()).isEqualTo(2);
        assertThat(metrics.averageLikeCount().value()).isEqualByComparingTo("15.00");
        assertThat(metrics.averageLikeCount().sampleCount()).isEqualTo(2);
        assertThat(metrics.averageCommentCount().value()).isEqualByComparingTo("6.00");
        assertThat(metrics.averageCommentCount().sampleCount()).isEqualTo(2);
        assertThat(metrics.engagementRate().value()).isEqualByComparingTo("15.00");
        assertThat(metrics.engagementRate().sampleCount()).isEqualTo(1);
        assertThat(metrics.contentFormats())
                .extracting(format -> format.contentType() + ":" + format.count())
                .containsExactly("POST:1", "REELS:1", "UNKNOWN:1");
        assertThat(result.profileUrl())
                .isEqualTo("https://www.instagram.com/creator.handle/");
        assertThat(result.profileImageUrl())
                .isEqualTo("https://cdn.example.com/profile.jpg");
        assertThat(result.contents()).hasSize(5);
    }

    @Test
    void detailKeepsUncollectedMetricsNullInsteadOfTreatingThemAsZero() {
        ReflectionTestUtils.setField(application, "engagementRate", null);
        when(applicationRepository.findById(1L)).thenReturn(Optional.of(application));
        when(userRepository.findById(10L)).thenReturn(Optional.of(user));
        when(generationRepository.findById(20L)).thenReturn(Optional.of(generation));
        when(mediaRepository.findAllByApplicationIdOrderBySequenceNoAscMediaSequenceNoAsc(1L))
                .thenReturn(List.of());

        var metrics = service.findDetail(1L).metrics();

        assertThat(metrics.recent90DayContentCount()).isNull();
        assertThat(metrics.lastPublishedAt()).isNull();
        assertThat(metrics.uploadCadence().dailyAverage()).isNull();
        assertThat(metrics.averageViewCount().value()).isNull();
        assertThat(metrics.averageViewCount().sampleCount()).isZero();
        assertThat(metrics.engagementRate().value()).isNull();
        assertThat(metrics.engagementRate().sampleCount()).isZero();
    }

    private ApplicationMedia media(
            String contentId,
            int sequenceNo,
            int mediaSequenceNo,
            LocalDateTime publishedAt,
            ContentType contentType,
            Long views,
            Long likes,
            Long comments) {
        return ApplicationMedia.builder()
                .applicationId(1L)
                .snsCode(SnsPlatform.INSTAGRAM)
                .snsContentId(contentId)
                .snsMediaId(contentId + "-media-" + mediaSequenceNo)
                .contentUrl("https://example.com/" + contentId)
                .contentType(contentType)
                .mediaType(MediaType.IMAGE)
                .sequenceNo(sequenceNo)
                .mediaSequenceNo(mediaSequenceNo)
                .publishedAt(publishedAt)
                .viewCount(views)
                .likeCount(likes)
                .commentCount(comments)
                .collectedAt(COLLECTED_AT)
                .build();
    }
}
