package com.fuma.hiselectors.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fuma.hiselectors.application.dto.ApplicationCreateRequest;
import com.fuma.hiselectors.application.dto.ApplicationResponse;
import com.fuma.hiselectors.application.model.Application;
import com.fuma.hiselectors.application.model.ApplicationStatus;
import com.fuma.hiselectors.application.model.MediaCollectionStatus;
import com.fuma.hiselectors.application.model.SnsPlatform;
import com.fuma.hiselectors.application.repository.ApplicationRepository;
import com.fuma.hiselectors.content.client.ContentFetcher;
import com.fuma.hiselectors.exception.BusinessException;
import com.fuma.hiselectors.exception.ErrorCode;
import com.fuma.hiselectors.generation.model.Generation;
import com.fuma.hiselectors.generation.repository.GenerationRepository;
import com.fuma.hiselectors.oauth.OAuthStateProvider;
import com.fuma.hiselectors.selectors.model.Selectors;
import com.fuma.hiselectors.selectors.repository.SelectorsRepository;
import com.fuma.hiselectors.user.model.User;
import com.fuma.hiselectors.user.repository.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.security.crypto.password.PasswordEncoder;

class ApplicationServiceTest {

    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-08-20T03:00:00Z"), ZoneId.of("Asia/Seoul"));
    private static final LocalDateTime NOW = LocalDateTime.now(CLOCK);

    private final ApplicationRepository applicationRepository = mock(ApplicationRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final GenerationRepository generationRepository = mock(GenerationRepository.class);
    private final SelectorsRepository selectorsRepository = mock(SelectorsRepository.class);
    private final ContentFetcher instagramFetcher = mock(ContentFetcher.class);
    private final ContentFetcher youtubeFetcher = mock(ContentFetcher.class);
    private final OAuthStateProvider oAuthStateProvider = mock(OAuthStateProvider.class);
    private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
    private final ApplicationService service = new ApplicationService(
            applicationRepository, userRepository, generationRepository, selectorsRepository,
            List.of(instagramFetcher, youtubeFetcher),
            oAuthStateProvider, passwordEncoder, CLOCK);

    private ApplicationCreateRequest request() {
        return new ApplicationCreateRequest("verification-token", true, true);
    }

    private void stubVerifiedAccount() {
        when(oAuthStateProvider.resolveVerificationToken("verification-token", "hi-user"))
                .thenReturn(new OAuthStateProvider.VerifiedAccount(
                        "hi-user", SnsPlatform.YOUTUBE, "UC123", 100L, 42L));
    }

    private void stubActiveGeneration() {
        when(generationRepository
                .findFirstByStartDateLessThanEqualAndEndDateGreaterThanEqualAndStatusOrderByStartDateAsc(
                        any(), any(), any()))
                .thenReturn(Optional.of(Generation.builder().generationName("1기").build()));
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', textBlock = """
            https://instagram.com/creator.name/|INSTAGRAM|creator.name|https://www.instagram.com/creator.name/
            https://m.youtube.com/@creator?feature=shared|YOUTUBE|@creator|https://www.youtube.com/@creator
            https://www.youtube.com/channel/UC_x5XG1OV2P6uZZ5FSM9Ttw|YOUTUBE|UC_x5XG1OV2P6uZZ5FSM9Ttw|https://www.youtube.com/channel/UC_x5XG1OV2P6uZZ5FSM9Ttw
            """)
    void createsPendingTestApplicationFromSupportedProfileUrl(
            String profileUrl, SnsPlatform platform, String accountId, String canonicalUrl) {
        stubTestCreation();
        stubContentFetchers();
        ContentFetcher fetcher = platform == SnsPlatform.INSTAGRAM
                ? instagramFetcher : youtubeFetcher;
        when(fetcher.fetchProfile(accountId)).thenReturn(new ContentFetcher.Profile(
                "https://cdn.example.com/profile.jpg", 12_345L, 120L));

        assertThat(service.createTest(profileUrl)).isEqualTo(31L);

        ArgumentCaptor<User> user = ArgumentCaptor.forClass(User.class);
        ArgumentCaptor<Application> application = ArgumentCaptor.forClass(Application.class);
        verify(userRepository).save(user.capture());
        verify(applicationRepository).save(application.capture());
        assertThat(user.getValue().getHiId()).startsWith("test_").hasSize(20);
        assertThat(user.getValue().getName()).startsWith("[테스트] ");
        assertThat(user.getValue().getAlimtalk()).isEqualTo("N");
        assertThat(application.getValue()).satisfies(saved -> {
            assertThat(saved.getUserId()).isEqualTo(7L);
            assertThat(saved.getGenerationId()).isEqualTo(2L);
            assertThat(saved.getSnsCode()).isEqualTo(platform);
            assertThat(saved.getSnsAccountId()).isEqualTo(accountId);
            assertThat(saved.getProfileUrl()).isEqualTo(canonicalUrl);
            assertThat(saved.getProfileImageUrl())
                    .isEqualTo("https://cdn.example.com/profile.jpg");
            assertThat(saved.getFollowerCount()).isEqualTo(12_345L);
            assertThat(saved.getContentCount()).isEqualTo(120L);
            assertThat(saved.isAlarmYn()).isFalse();
            assertThat(saved.getPolicyAgreedAt()).isEqualTo(NOW);
            assertThat(saved.getStatus()).isEqualTo(ApplicationStatus.PENDING);
            assertThat(saved.getMediaCollectionStatus()).isEqualTo(MediaCollectionStatus.PENDING);
        });
        verify(fetcher).fetchProfile(accountId);
    }

    @Test
    void createsTestApplicationWhenPublicProfileLookupFails() {
        stubTestCreation();
        stubContentFetchers();
        when(instagramFetcher.fetchProfile("creator.name"))
                .thenThrow(new IllegalStateException("profile API failed"));

        assertThat(service.createTest("https://instagram.com/creator.name/"))
                .isEqualTo(31L);

        ArgumentCaptor<Application> application = ArgumentCaptor.forClass(Application.class);
        verify(applicationRepository).save(application.capture());
        assertThat(application.getValue()).satisfies(saved -> {
            assertThat(saved.getProfileImageUrl()).isNull();
            assertThat(saved.getFollowerCount()).isNull();
            assertThat(saved.getContentCount()).isNull();
            assertThat(saved.getStatus()).isEqualTo(ApplicationStatus.PENDING);
        });
    }

    @Test
    void rejectsNonProfileOrUntrustedTestUrlsBeforeWriting() {
        for (String value : java.util.List.of(
                "http://instagram.com/creator",
                "https://instagram.com/p/post-id",
                "https://youtube.com/watch?v=video-id",
                "https://youtube.com.evil.example/@creator")) {
            assertThatThrownBy(() -> service.createTest(value))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.INVALID_INPUT);
        }
        verifyNoInteractions(userRepository, applicationRepository, generationRepository);
    }

    @Test
    void submitsApplicationWithActiveGenerationAndPendingStatus() {
        when(userRepository.findByHiId("hi-user"))
                .thenReturn(Optional.of(User.builder().hiId("hi-user").build()));
        stubVerifiedAccount();
        stubActiveGeneration();
        when(applicationRepository.save(any(Application.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        ApplicationResponse response = service.create("hi-user", request());

        assertThat(response.snsCode()).isEqualTo(SnsPlatform.YOUTUBE);
        assertThat(response.snsAccountId()).isEqualTo("UC123");
        assertThat(response.profileUrl())
                .isEqualTo("https://www.youtube.com/channel/UC123");
        assertThat(response.followerCount()).isEqualTo(100L);
        assertThat(response.contentCount()).isEqualTo(42L);
        assertThat(response.lastContentAt()).isNull();
        assertThat(response.engagementRate()).isNull();
        assertThat(response.alarmYn()).isTrue();
        assertThat(response.policyAgreedAt()).isEqualTo(NOW);
        assertThat(response.status()).isEqualTo(ApplicationStatus.PENDING);
        var saved = org.mockito.ArgumentCaptor.forClass(Application.class);
        verify(applicationRepository).save(saved.capture());
        assertThat(saved.getValue().getMediaCollectionStatus())
                .isEqualTo(MediaCollectionStatus.PENDING);
        assertThat(saved.getValue().getContentCount()).isEqualTo(42L);
        verify(generationRepository)
                .findFirstByStartDateLessThanEqualAndEndDateGreaterThanEqualAndStatusOrderByStartDateAsc(
                        eq(NOW), eq(NOW), any());
    }

    @Test
    void rejectsWhenUserNotFound() {
        when(userRepository.findByHiId("ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create("ghost", request()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.APPLICATION_USER_NOT_FOUND);
    }

    @Test
    void rejectsWhenNoActiveGeneration() {
        when(userRepository.findByHiId("hi-user"))
                .thenReturn(Optional.of(User.builder().hiId("hi-user").build()));
        stubVerifiedAccount();
        when(generationRepository
                .findFirstByStartDateLessThanEqualAndEndDateGreaterThanEqualAndStatusOrderByStartDateAsc(
                        any(), any(), any()))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create("hi-user", request()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.ACTIVE_GENERATION_NOT_FOUND);
    }

    @Test
    void rejectsBlacklistedSelector() {
        User user = User.builder().hiId("hi-user").build();
        ReflectionTestUtils.setField(user, "id", 7L);
        Selectors selectors = mock(Selectors.class);
        when(selectors.isBlacklisted()).thenReturn(true);
        when(userRepository.findByHiId("hi-user")).thenReturn(Optional.of(user));
        when(selectorsRepository.findByUserId(7L)).thenReturn(Optional.of(selectors));

        assertThatThrownBy(() -> service.create("hi-user", request()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.BLACKLISTED_SELECTOR);

        verify(applicationRepository, never()).save(any());
    }

    @Test
    void rejectsWhenAlreadyAppliedToGeneration() {
        User user = User.builder().hiId("hi-user").build();
        ReflectionTestUtils.setField(user, "id", 7L);
        Generation generation = Generation.builder().generationName("2기").build();
        ReflectionTestUtils.setField(generation, "id", 2L);

        when(userRepository.findByHiId("hi-user")).thenReturn(Optional.of(user));
        stubVerifiedAccount();
        when(generationRepository
                .findFirstByStartDateLessThanEqualAndEndDateGreaterThanEqualAndStatusOrderByStartDateAsc(
                        any(), any(), any()))
                .thenReturn(Optional.of(generation));
        when(applicationRepository.existsByUserIdAndGenerationId(eq(7L), eq(2L)))
                .thenReturn(true);

        assertThatThrownBy(() -> service.create("hi-user", request()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.DUPLICATE_APPLICATION);

        verify(applicationRepository, never()).save(any());
    }

    @Test
    void rejectsInvalidOrDifferentUserVerificationToken() {
        when(userRepository.findByHiId("hi-user"))
                .thenReturn(Optional.of(User.builder().hiId("hi-user").build()));
        when(oAuthStateProvider.resolveVerificationToken("verification-token", "hi-user"))
                .thenThrow(new IllegalArgumentException("invalid token"));

        assertThatThrownBy(() -> service.create("hi-user", request()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.OAUTH_VERIFICATION_INVALID);

        verify(generationRepository, never())
                .findFirstByStartDateLessThanEqualAndEndDateGreaterThanEqualAndStatusOrderByStartDateAsc(
                        any(), any(), any());
        verify(applicationRepository, never()).save(any());
    }

    private void stubTestCreation() {
        Generation generation = Generation.builder().generationName("2기").build();
        ReflectionTestUtils.setField(generation, "id", 2L);
        when(generationRepository
                .findFirstByStartDateLessThanEqualAndEndDateGreaterThanEqualAndStatusOrderByStartDateAsc(
                        NOW, NOW, com.fuma.hiselectors.generation.model.GenerationStatus.ACTIVE))
                .thenReturn(Optional.of(generation));
        when(passwordEncoder.encode(any())).thenReturn("encoded-password");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 7L);
            return saved;
        });
        when(applicationRepository.save(any(Application.class))).thenAnswer(invocation -> {
            Application saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 31L);
            return saved;
        });
    }

    private void stubContentFetchers() {
        when(instagramFetcher.supports()).thenReturn(SnsPlatform.INSTAGRAM);
        when(youtubeFetcher.supports()).thenReturn(SnsPlatform.YOUTUBE);
    }
}
