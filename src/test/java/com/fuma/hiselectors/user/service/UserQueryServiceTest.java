package com.fuma.hiselectors.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fuma.hiselectors.exception.BusinessException;
import com.fuma.hiselectors.exception.ErrorCode;
import com.fuma.hiselectors.user.dto.UserMeResponse;
import com.fuma.hiselectors.user.model.User;
import com.fuma.hiselectors.user.repository.UserRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class UserQueryServiceTest {

    private UserRepository userRepository;
    private UserQueryService userQueryService;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        userQueryService = new UserQueryService(userRepository);
    }

    @Test
    void returnsMemberFieldsWithoutPassword() {
        User user = User.builder()
                .hiId("hiuser1")
                .hiPassword("secret")
                .name("홍길동")
                .email("hong@example.com")
                .phone("01012345678")
                .alimtalk("Y")
                .build();
        when(userRepository.findByHiId("hiuser1")).thenReturn(Optional.of(user));

        UserMeResponse response = userQueryService.getMe("hiuser1");

        assertThat(response.hiId()).isEqualTo("hiuser1");
        assertThat(response.name()).isEqualTo("홍길동");
        assertThat(response.email()).isEqualTo("hong@example.com");
        assertThat(response.phone()).isEqualTo("01012345678");
        assertThat(response.alimtalk()).isEqualTo("Y");
        assertThat(response.toString()).doesNotContain("secret");
    }

    @Test
    void throwsWhenUserIsMissing() {
        when(userRepository.findByHiId("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userQueryService.getMe("missing"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.APPLICATION_USER_NOT_FOUND);
    }
}
