package com.fuma.hiselectors.application.service;

import com.fuma.hiselectors.application.dto.ApplicationCreateRequest;
import com.fuma.hiselectors.application.dto.ApplicationResponse;
import com.fuma.hiselectors.application.model.Application;
import com.fuma.hiselectors.application.repository.ApplicationRepository;
import com.fuma.hiselectors.exception.BusinessException;
import com.fuma.hiselectors.exception.ErrorCode;
import com.fuma.hiselectors.user.model.User;
import com.fuma.hiselectors.user.repository.UserRepository;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ApplicationService {

    private final ApplicationRepository applicationRepository;
    private final UserRepository userRepository;

    @Transactional
    public ApplicationResponse create(String loginId, ApplicationCreateRequest request) {
        User user = userRepository.findByHiId(loginId)
                .orElseThrow(() -> new BusinessException(ErrorCode.APPLICATION_USER_NOT_FOUND));

        Application application = Application.builder()
                .userId(user.getId())
                .alarmYn(request.alarmAgreed())
                .policyAgreedAt(LocalDateTime.now())
                .build();

        return ApplicationResponse.from(applicationRepository.save(application));
    }
}
