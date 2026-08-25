package com.fuma.hiselectors.notification.service;

import com.fuma.hiselectors.admin.model.Admin;
import com.fuma.hiselectors.admin.repository.AdminRepository;
import com.fuma.hiselectors.exception.BusinessException;
import com.fuma.hiselectors.exception.ErrorCode;
import com.fuma.hiselectors.kakao.config.KakaoMessageProperties;
import com.fuma.hiselectors.kakao.model.KakaoRecipientStatus;
import com.fuma.hiselectors.kakao.model.UserKakaoRecipient;
import com.fuma.hiselectors.kakao.repository.UserKakaoRecipientRepository;
import com.fuma.hiselectors.kakao.service.KakaoRecipientStatusService;
import com.fuma.hiselectors.notification.dto.NotificationMessageCommand;
import com.fuma.hiselectors.notification.dto.NotificationSendResponse;
import com.fuma.hiselectors.notification.model.KakaoTemplateType;
import com.fuma.hiselectors.notification.model.Notification;
import com.fuma.hiselectors.notification.model.NotificationChannel;
import com.fuma.hiselectors.notification.model.NotificationInitiatorType;
import com.fuma.hiselectors.notification.model.NotificationStatus;
import com.fuma.hiselectors.notification.repository.NotificationRepository;
import com.fuma.hiselectors.notification.sender.NotificationSender;
import java.time.LocalDateTime;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private static final KakaoTemplateType DEFAULT_TEMPLATE_TYPE = KakaoTemplateType.TEXT;

    private final AdminRepository adminRepository;
    private final UserKakaoRecipientRepository recipientRepository;
    private final KakaoTemplateFactoryResolver templateFactoryResolver;
    private final NotificationRecorder recorder;
    private final NotificationSender notificationSender;
    private final KakaoRecipientStatusService recipientStatusService;
    private final NotificationRepository notificationRepository;
    private final TextTemplateFactory textTemplateFactory;
    private final KakaoMessageProperties messageProperties;

    public NotificationSendResponse sendToMe(String adminLoginId,
                                               NotificationMessageCommand command) {
        Admin admin = adminRepository.findByLoginId(adminLoginId)
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
        return sendToMe(admin, command);
    }

    public NotificationSendResponse sendToFriend(String adminLoginId,
                                                   NotificationMessageCommand command) {
        Admin admin = adminRepository.findByLoginId(adminLoginId)
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
        return sendToFriend(admin, command, NotificationInitiatorType.ADMIN, admin.getId());
    }

    public NotificationSendResponse sendToFriendAsSystem(String senderAdminLoginId,
                                                          NotificationMessageCommand command) {
        Admin admin = adminRepository.findByLoginId(senderAdminLoginId)
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
        return sendToFriend(admin, command, NotificationInitiatorType.SYSTEM, null);
    }

    @Transactional
    public NotificationSendResponse resendFailed(String adminLoginId, Long notificationId) {
        Admin admin = adminRepository.findByLoginId(adminLoginId)
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));

        if (notification.getStatus() != NotificationStatus.FAILED) {
            throw new BusinessException(ErrorCode.INVALID_INPUT,
                    "실패한 발송 이력만 재발송할 수 있습니다.");
        }
        if (notification.getNotificationChannel() != NotificationChannel.KAKAO_MESSAGE) {
            throw new BusinessException(ErrorCode.INVALID_INPUT,
                    "카카오 메시지 발송 이력만 재발송할 수 있습니다.");
        }

        UserKakaoRecipient recipient = recipientRepository
                .findByKakaoMessageUuid(notification.getReceiver())
                .orElseThrow(() -> new BusinessException(ErrorCode.KAKAO_RECIPIENT_NOT_FOUND));
        if (recipient.getStatus() != KakaoRecipientStatus.READY) {
            throw new BusinessException(ErrorCode.KAKAO_RECIPIENT_NOT_READY);
        }

        Long connectionId = requireConnection(admin);
        try {
            notificationSender.sendToFriend(connectionId, recipient.getKakaoMessageUuid(),
                    textTemplateFactory.createReplayTemplate(notification.getBody()));
            notification.markSent(LocalDateTime.now());
            notificationRepository.save(notification);
            return new NotificationSendResponse(notification.getId(), NotificationStatus.SENT);
        } catch (BusinessException e) {
            if (e.getErrorCode() == ErrorCode.KAKAO_RECIPIENT_INVALID) {
                recipientStatusService.markReauthRequired(recipient.getId());
            }
            notification.markFailed();
            notificationRepository.save(notification);
            throw e;
        } catch (RuntimeException e) {
            notification.markFailed();
            notificationRepository.save(notification);
            throw e;
        }
    }

    // 메시지 발송 (승인/반려 결과 알림)
    public NotificationSendResponse sendToUuid(String adminLoginId, String receiverUuid,
                                               NotificationMessageCommand command) {
        Admin admin = adminRepository.findByLoginId(adminLoginId)
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
        Long connectionId = requireConnection(admin);
        CreatedKakaoTemplate created = templateFactoryResolver.create(
                DEFAULT_TEMPLATE_TYPE, command);
        Long notificationId = recorder.createRequested(
                command.notificationType().getPurposeCode(), command.referenceId(),
                receiverUuid, created.body(),
                NotificationInitiatorType.ADMIN, admin.getId());
        try {
            notificationSender.sendToFriend(connectionId, receiverUuid, created.template());
            recorder.markSent(notificationId);
            return new NotificationSendResponse(notificationId, NotificationStatus.SENT);
        } catch (RuntimeException e) {
            recorder.markFailed(notificationId);
            throw e;
        }
    }

    private NotificationSendResponse sendToMe(Admin admin, NotificationMessageCommand command) {
        Long connectionId = requireConnection(admin);
        CreatedKakaoTemplate created = templateFactoryResolver.create(
                DEFAULT_TEMPLATE_TYPE, command);
        Long notificationId = recorder.createRequested(
                command.notificationType().getPurposeCode(), command.referenceId(),
                "ME:" + connectionId, created.body(),
                NotificationInitiatorType.ADMIN, admin.getId());
        try {
            notificationSender.sendToMe(connectionId, created.template());
            recorder.markSent(notificationId);
            return new NotificationSendResponse(notificationId, NotificationStatus.SENT);
        } catch (RuntimeException e) {
            recorder.markFailed(notificationId);
            throw e;
        }
    }

    private NotificationSendResponse sendToFriend(Admin admin,
                                                    NotificationMessageCommand command,
                                                    NotificationInitiatorType initiatedByType,
                                                    Long initiatedById) {
        Long connectionId = requireConnection(admin);
        if (command.recipientUserId() == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "수신 사용자 ID가 필요합니다.");
        }
        ResolvedRecipient recipient = resolveRecipient(command.recipientUserId());

        CreatedKakaoTemplate created = templateFactoryResolver.create(
                DEFAULT_TEMPLATE_TYPE, command);
        Long notificationId = recorder.createRequested(
                command.notificationType().getPurposeCode(), command.referenceId(),
                recipient.uuid(), created.body(),
                initiatedByType, initiatedById);
        try {
            notificationSender.sendToFriend(connectionId, recipient.uuid(),
                    created.template());
            recorder.markSent(notificationId);
            return new NotificationSendResponse(notificationId, NotificationStatus.SENT);
        } catch (BusinessException e) {
            recorder.markFailed(notificationId);
            if (e.getErrorCode() == ErrorCode.KAKAO_RECIPIENT_INVALID
                    && recipient.recipientId() != null) {
                recipientStatusService.markReauthRequired(recipient.recipientId());
            }
            throw e;
        } catch (RuntimeException e) {
            recorder.markFailed(notificationId);
            throw e;
        }
    }

    private ResolvedRecipient resolveRecipient(Long recipientUserId) {
        UserKakaoRecipient recipient = recipientRepository.findByUserId(recipientUserId)
                .orElse(null);
        if (recipient != null) {
            if (recipient.getStatus() != KakaoRecipientStatus.READY) {
                throw new BusinessException(ErrorCode.KAKAO_RECIPIENT_NOT_READY);
            }
            return new ResolvedRecipient(recipient.getKakaoMessageUuid(), recipient.getId());
        }

        String defaultRecipientUuid = messageProperties.defaultRecipientUuid();
        if (defaultRecipientUuid == null || defaultRecipientUuid.isBlank()) {
            throw new BusinessException(ErrorCode.KAKAO_RECIPIENT_NOT_FOUND);
        }
        log.warn("Kakao recipient connection not found; using default recipient: userId={}",
                recipientUserId);
        return new ResolvedRecipient(defaultRecipientUuid, null);
    }

    private Long requireConnection(Admin admin) {
        if (admin.getKakaoSenderConnectionId() == null) {
            throw new BusinessException(ErrorCode.KAKAO_SENDER_NOT_CONNECTED);
        }
        return admin.getKakaoSenderConnectionId();
    }

    private record ResolvedRecipient(String uuid, Long recipientId) {
    }
}
