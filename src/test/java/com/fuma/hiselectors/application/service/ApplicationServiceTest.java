package com.fuma.hiselectors.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fuma.hiselectors.application.dto.ApplicationCreateRequest;
import com.fuma.hiselectors.application.dto.ApplicationResponse;
import com.fuma.hiselectors.application.model.Application;
import com.fuma.hiselectors.application.repository.ApplicationRepository;
import com.fuma.hiselectors.exception.BusinessException;
import com.fuma.hiselectors.exception.ErrorCode;
import com.fuma.hiselectors.user.model.User;
import com.fuma.hiselectors.user.repository.UserRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ApplicationServiceTest {

    private final ApplicationRepository applicationRepository = mock(ApplicationRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final ApplicationService service =
            new ApplicationService(applicationRepository, userRepository);

    @Test
    void recordsConsentTimestampAndAlarmFlagOnCreate() {
        User user = User.builder().hiId("hi-user").build();
        when(userRepository.findByHiId("hi-user")).thenReturn(Optional.of(user));
        when(applicationRepository.save(any(Application.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        ApplicationResponse response =
                service.create("hi-user", new ApplicationCreateRequest(true, true));

        assertThat(response.alarmYn()).isTrue();
        assertThat(response.policyAgreedAt()).isNotNull();
    }

    @Test
    void rejectsWhenUserNotFound() {
        when(userRepository.findByHiId("ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create("ghost", new ApplicationCreateRequest(true, false)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.APPLICATION_USER_NOT_FOUND);
    }
}
