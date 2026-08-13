package com.fuma.hiselectors.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fuma.hiselectors.application.dto.ApplicationCreateRequest;
import com.fuma.hiselectors.application.dto.ApplicationResponse;
import com.fuma.hiselectors.application.model.Application;
import com.fuma.hiselectors.application.model.ApplicationStatus;
import com.fuma.hiselectors.application.model.SnsPlatform;
import com.fuma.hiselectors.application.repository.ApplicationRepository;
import com.fuma.hiselectors.exception.BusinessException;
import com.fuma.hiselectors.exception.ErrorCode;
import com.fuma.hiselectors.generation.model.Generation;
import com.fuma.hiselectors.generation.repository.GenerationRepository;
import com.fuma.hiselectors.user.model.User;
import com.fuma.hiselectors.user.repository.UserRepository;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ApplicationServiceTest {

    private final ApplicationRepository applicationRepository = mock(ApplicationRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final GenerationRepository generationRepository = mock(GenerationRepository.class);
    private final ApplicationService service =
            new ApplicationService(applicationRepository, userRepository, generationRepository);

    private ApplicationCreateRequest request() {
        return new ApplicationCreateRequest(
                SnsPlatform.YOUTUBE, "UC123", 100L,
                LocalDateTime.of(2026, 8, 1, 0, 0), new java.math.BigDecimal("3.50"),
                true, true);
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
        stubActiveGeneration();
        when(applicationRepository.save(any(Application.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        ApplicationResponse response = service.create("hi-user", request());

        assertThat(response.snsCode()).isEqualTo(SnsPlatform.YOUTUBE);
        assertThat(response.snsAccountId()).isEqualTo("UC123");
        assertThat(response.followerCount()).isEqualTo(100L);
        assertThat(response.lastContentAt()).isEqualTo(LocalDateTime.of(2026, 8, 1, 0, 0));
        assertThat(response.engagementRate()).isEqualByComparingTo("3.50");
        assertThat(response.alarmYn()).isTrue();
        assertThat(response.policyAgreedAt()).isNotNull();
        assertThat(response.status()).isEqualTo(ApplicationStatus.PENDING);
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
        when(generationRepository
                .findFirstByStartDateLessThanEqualAndEndDateGreaterThanEqualAndStatusOrderByStartDateAsc(
                        any(), any(), any()))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create("hi-user", request()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.ACTIVE_GENERATION_NOT_FOUND);
    }
}
