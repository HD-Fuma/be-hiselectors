package com.fuma.hiselectors.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fuma.hiselectors.application.dto.ApplicationCreateRequest;
import com.fuma.hiselectors.application.dto.ApplicationResponse;
import com.fuma.hiselectors.application.model.Application;
import com.fuma.hiselectors.application.model.ApplicationStatus;
import com.fuma.hiselectors.application.model.MediaCollectionStatus;
import com.fuma.hiselectors.application.model.SnsPlatform;
import com.fuma.hiselectors.application.repository.ApplicationRepository;
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
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class ApplicationServiceTest {

    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-08-20T03:00:00Z"), ZoneId.of("Asia/Seoul"));
    private static final LocalDateTime NOW = LocalDateTime.now(CLOCK);

    private final ApplicationRepository applicationRepository = mock(ApplicationRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final GenerationRepository generationRepository = mock(GenerationRepository.class);
    private final SelectorsRepository selectorsRepository = mock(SelectorsRepository.class);
    private final OAuthStateProvider oAuthStateProvider = mock(OAuthStateProvider.class);
    private final ApplicationService service = new ApplicationService(
            applicationRepository, userRepository, generationRepository, selectorsRepository,
            oAuthStateProvider, CLOCK);

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
}
