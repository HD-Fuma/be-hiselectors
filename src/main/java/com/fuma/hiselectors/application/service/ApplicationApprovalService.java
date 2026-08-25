package com.fuma.hiselectors.application.service;

import com.fuma.hiselectors.application.dto.ApplicationResponse;
import com.fuma.hiselectors.application.dto.ApplicationStatusUpdateRequest;
import com.fuma.hiselectors.application.model.Application;
import com.fuma.hiselectors.application.model.ApplicationStatus;
import com.fuma.hiselectors.application.repository.ApplicationReportRepository;
import com.fuma.hiselectors.application.repository.ApplicationRepository;
import com.fuma.hiselectors.exception.BusinessException;
import com.fuma.hiselectors.exception.ErrorCode;
import com.fuma.hiselectors.notification.dto.NotificationMessageCommand;
import com.fuma.hiselectors.notification.model.NotificationType;
import com.fuma.hiselectors.notification.service.NotificationService;
import com.fuma.hiselectors.selectors.model.Selectors;
import com.fuma.hiselectors.selectors.model.SelectorsGeneration;
import com.fuma.hiselectors.selectors.model.SelectorsSnsAccount;
import com.fuma.hiselectors.selectors.repository.SelectorsGenerationRepository;
import com.fuma.hiselectors.selectors.repository.SelectorsRepository;
import com.fuma.hiselectors.selectors.repository.SelectorsSnsAccountRepository;
import com.fuma.hiselectors.user.model.User;
import com.fuma.hiselectors.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ApplicationApprovalService {

    // ponytail: 테스트용 고정 수신자 uuid. 지원자별 uuid 조회 붙기 전까지 이 한 명에게만 발송.
    private static final String TEST_RECIPIENT_UUID = "_cz_yfzN_Mr51eLT6tjv2urZ9cTxxfTA8sZH";
    private static final Logger log = LoggerFactory.getLogger(ApplicationApprovalService.class);

    private final ApplicationRepository applicationRepository;
    private final ApplicationReportRepository applicationReportRepository;
    private final UserRepository userRepository;
    private final SelectorsRepository selectorsRepository;
    private final SelectorsGenerationRepository selectorsGenerationRepository;
    private final SelectorsSnsAccountRepository selectorsSnsAccountRepository;
    private final NotificationService notificationService;

    @Transactional
    public ApplicationResponse updateStatus(
            Long applicationId, ApplicationStatusUpdateRequest request, String adminLoginId) {
        Application application = applicationRepository.findByIdForUpdate(applicationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.APPLICATION_NOT_FOUND));
        if (application.getStatus() == request.status()) {
            return ApplicationResponse.from(application);
        }
        if (request.status() == ApplicationStatus.APPROVED) {
            approve(application);
        }
        application.changeStatus(request.status());
        notifyResult(application, request.status(), adminLoginId);
        return ApplicationResponse.from(application);
    }

    // 승인/반려 결과 카카오 알림. 발송 실패가 승인/반려 자체를 롤백시키지 않도록 격리.
    private void notifyResult(Application application, ApplicationStatus status,
                              String adminLoginId) {
        NotificationType type = switch (status) {
            case APPROVED -> NotificationType.SELECTION_APPROVED;
            case REJECTED -> NotificationType.SELECTION_REJECTED;
            default -> null;
        };
        if (type == null) {
            return;
        }
        String name = userRepository.findById(application.getUserId())
                .map(User::getName).orElse("");
        NotificationMessageCommand command = new NotificationMessageCommand(
                null, application.getUserId(), application.getId(), name, null, type);
        // ponytail: 디버그용. 원인 확인되면 catch-and-log 로 되돌린다.
        log.warn("승인/반려 카카오 알림 발송 시도 applicationId={} status={} adminLoginId={}",
                application.getId(), status, adminLoginId);
        notificationService.sendToUuid(adminLoginId, TEST_RECIPIENT_UUID, command);
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
        applicationReportRepository
                .findFirstByApplicationIdOrderByCreatedAtDesc(application.getId())
                .ifPresent(report -> selectors.assignCategory(report.getCategory()));
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
                                application.getFollowerCount(),
                                application.getProfileUrl(),
                                application.getProfileImageUrl()),
                        () -> selectorsSnsAccountRepository.save(
                        SelectorsSnsAccount.builder()
                                .selectorsId(selectorsId)
                                .snsCode(application.getSnsCode())
                                .accountId(application.getSnsAccountId())
                                .followerCount(application.getFollowerCount())
                                .profileUrl(application.getProfileUrl())
                                .profileImageUrl(application.getProfileImageUrl())
                                .build()));
    }

    private Selectors createSelectors(Application application, User user) {
        String code = "RC%09dT".formatted(application.getId() * 2003L - 806L);
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
