package com.fuma.hiselectors.notification.service;

import com.fuma.hiselectors.admin.model.Admin;
import com.fuma.hiselectors.admin.repository.AdminRepository;
import com.fuma.hiselectors.exception.BusinessException;
import com.fuma.hiselectors.exception.ErrorCode;
import com.fuma.hiselectors.kakao.model.KakaoRecipientStatus;
import com.fuma.hiselectors.kakao.model.UserKakaoRecipient;
import com.fuma.hiselectors.kakao.repository.UserKakaoRecipientRepository;
import com.fuma.hiselectors.kakao.service.KakaoRecipientStatusService;
import com.fuma.hiselectors.notification.dto.NotificationMessageCommand;
import com.fuma.hiselectors.notification.dto.NotificationSendResponse;
import com.fuma.hiselectors.notification.model.KakaoTemplateType;
import com.fuma.hiselectors.notification.model.NotificationStatus;
import com.fuma.hiselectors.notification.sender.NotificationSender;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

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
        return sendToFriend(admin, command);
    }

    private NotificationSendResponse sendToMe(Admin admin, NotificationMessageCommand command) {
        Long connectionId = requireConnection(admin);
        CreatedKakaoTemplate created = templateFactoryResolver.create(
                DEFAULT_TEMPLATE_TYPE, command);
        Long notificationId = recorder.createRequested(
                command.notificationType().getPurposeCode(), command.referenceId(),
                "ME:" + connectionId, created.body());
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
                                                    NotificationMessageCommand command) {
        Long connectionId = requireConnection(admin);
        if (command.recipientUserId() == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "수신 사용자 ID가 필요합니다.");
        }
        UserKakaoRecipient recipient = recipientRepository.findByUserId(command.recipientUserId())
                .orElseThrow(() -> new BusinessException(ErrorCode.KAKAO_RECIPIENT_NOT_FOUND));
        if (recipient.getRecipientStatus() != KakaoRecipientStatus.READY) {
            throw new BusinessException(ErrorCode.KAKAO_RECIPIENT_NOT_READY);
        }

        CreatedKakaoTemplate created = templateFactoryResolver.create(
                DEFAULT_TEMPLATE_TYPE, command);
        Long notificationId = recorder.createRequested(
                command.notificationType().getPurposeCode(), command.referenceId(),
                recipient.getKakaoMessageUuid(), created.body());
        try {
            notificationSender.sendToFriend(connectionId, recipient.getKakaoMessageUuid(),
                    created.template());
            recorder.markSent(notificationId);
            return new NotificationSendResponse(notificationId, NotificationStatus.SENT);
        } catch (BusinessException e) {
            recorder.markFailed(notificationId);
            if (e.getErrorCode() == ErrorCode.KAKAO_RECIPIENT_INVALID) {
                recipientStatusService.markReauthRequired(recipient.getId());
            }
            throw e;
        } catch (RuntimeException e) {
            recorder.markFailed(notificationId);
            throw e;
        }
    }

    private Long requireConnection(Admin admin) {
        if (admin.getKakaoSenderConnectionId() == null) {
            throw new BusinessException(ErrorCode.KAKAO_SENDER_NOT_CONNECTED);
        }
        return admin.getKakaoSenderConnectionId();
    }
}
