package com.fuma.hiselectors.application.service;

import com.fuma.hiselectors.application.dto.ApplicationResponse;
import com.fuma.hiselectors.application.dto.ApplicationStatusUpdateRequest;
import com.fuma.hiselectors.application.model.Application;
import com.fuma.hiselectors.application.model.ApplicationStatus;
import com.fuma.hiselectors.application.repository.ApplicationRepository;
import com.fuma.hiselectors.exception.BusinessException;
import com.fuma.hiselectors.exception.ErrorCode;
import com.fuma.hiselectors.selectors.model.Selectors;
import com.fuma.hiselectors.selectors.model.SelectorsGeneration;
import com.fuma.hiselectors.selectors.model.SelectorsSnsAccount;
import com.fuma.hiselectors.selectors.repository.SelectorsGenerationRepository;
import com.fuma.hiselectors.selectors.repository.SelectorsRepository;
import com.fuma.hiselectors.selectors.repository.SelectorsSnsAccountRepository;
import com.fuma.hiselectors.user.model.User;
import com.fuma.hiselectors.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ApplicationApprovalService {

    private final ApplicationRepository applicationRepository;
    private final UserRepository userRepository;
    private final SelectorsRepository selectorsRepository;
    private final SelectorsGenerationRepository selectorsGenerationRepository;
    private final SelectorsSnsAccountRepository selectorsSnsAccountRepository;

    @Transactional
    public ApplicationResponse updateStatus(
            Long applicationId, ApplicationStatusUpdateRequest request) {
        Application application = applicationRepository.findByIdForUpdate(applicationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.APPLICATION_NOT_FOUND));
        if (application.getStatus() == request.status()) {
            return ApplicationResponse.from(application);
        }
        if (request.status() == ApplicationStatus.APPROVED) {
            approve(application);
        }
        application.changeStatus(request.status());
        return ApplicationResponse.from(application);
    }

    private void approve(Application application) {
        User user = userRepository.findByIdForUpdate(application.getUserId())
                .orElseThrow(() -> new BusinessException(ErrorCode.APPLICATION_USER_NOT_FOUND));
        Selectors selectors = selectorsRepository.findByUserIdForUpdate(application.getUserId())
                .orElseGet(() -> createSelectors(application, user));
        if (selectors.isBlacklisted()) {
            throw new BusinessException(ErrorCode.BLACKLISTED_SELECTOR);
        }
        selectors.activateForApplication(application.getId());
        if (!selectorsGenerationRepository.existsBySelectorsIdAndGenerationId(
                selectors.getId(), application.getGenerationId())) {
            selectorsGenerationRepository.save(SelectorsGeneration.builder()
                    .selectorsId(selectors.getId())
                    .generationId(application.getGenerationId())
                    .build());
        }
        synchronizeSnsAccount(selectors.getId(), application);
    }

    private void synchronizeSnsAccount(Long selectorsId, Application application) {
        selectorsSnsAccountRepository.findBySelectorsId(selectorsId)
                .ifPresentOrElse(account -> account.synchronize(
                                application.getSnsCode(),
                                application.getSnsAccountId(),
                                application.getFollowerCount()),
                        () -> selectorsSnsAccountRepository.save(
                        SelectorsSnsAccount.builder()
                                .selectorsId(selectorsId)
                                .snsCode(application.getSnsCode())
                                .accountId(application.getSnsAccountId())
                                .followerCount(application.getFollowerCount())
                                .build()));
    }

    private Selectors createSelectors(Application application, User user) {
        String code = "SEL-" + application.getId();
        if (code.length() > 20) {
            code = "SEL-" + Long.toString(application.getId(), 36);
        }
        String name = user.getName() == null ? "" : user.getName();
        try {
            return selectorsRepository.saveAndFlush(Selectors.builder()
                    .applicationId(application.getId())
                    .userId(application.getUserId())
                    .selectorsRoleId(Selectors.ACTIVE_ROLE)
                    .selectorsCode(code)
                    .selectorsNickname(name.substring(0, Math.min(name.length(), 20)))
                    .build());
        } catch (DataIntegrityViolationException exception) {
            throw new BusinessException(ErrorCode.SELECTOR_ALREADY_EXISTS);
        }
    }
}
