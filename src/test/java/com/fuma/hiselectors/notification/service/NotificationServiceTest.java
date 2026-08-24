package com.fuma.hiselectors.notification.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fuma.hiselectors.admin.model.Admin;
import com.fuma.hiselectors.admin.repository.AdminRepository;
import com.fuma.hiselectors.exception.BusinessException;
import com.fuma.hiselectors.exception.ErrorCode;
import com.fuma.hiselectors.kakao.dto.KakaoMessageTemplate;
import com.fuma.hiselectors.kakao.model.KakaoRecipientStatus;
import com.fuma.hiselectors.kakao.model.UserKakaoRecipient;
import com.fuma.hiselectors.kakao.repository.UserKakaoRecipientRepository;
import com.fuma.hiselectors.kakao.service.KakaoRecipientStatusService;
import com.fuma.hiselectors.notification.dto.NotificationMessageCommand;
import com.fuma.hiselectors.notification.model.KakaoTemplateType;
import com.fuma.hiselectors.notification.model.Notification;
import com.fuma.hiselectors.notification.model.NotificationChannel;
import com.fuma.hiselectors.notification.model.NotificationInitiatorType;
import com.fuma.hiselectors.notification.model.NotificationStatus;
import com.fuma.hiselectors.notification.model.NotificationType;
import com.fuma.hiselectors.notification.repository.NotificationRepository;
import com.fuma.hiselectors.notification.sender.NotificationSender;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class NotificationServiceTest {

    private final AdminRepository adminRepository = mock(AdminRepository.class);
    private final UserKakaoRecipientRepository recipientRepository =
            mock(UserKakaoRecipientRepository.class);
    private final KakaoTemplateFactoryResolver templateFactoryResolver =
            mock(KakaoTemplateFactoryResolver.class);
    private final NotificationRecorder recorder = mock(NotificationRecorder.class);
    private final NotificationSender notificationSender = mock(NotificationSender.class);
    private final KakaoRecipientStatusService recipientStatusService =
            mock(KakaoRecipientStatusService.class);
    private final NotificationRepository notificationRepository = mock(NotificationRepository.class);
    private final TextTemplateFactory textTemplateFactory = mock(TextTemplateFactory.class);
    private final KakaoMessageTemplate template = mock(KakaoMessageTemplate.class);
    private final NotificationMessageCommand command = new NotificationMessageCommand(
            null, 2L, 3L, "홍길동", null, NotificationType.SELECTION_APPROVED);

    private NotificationService service;
    private UserKakaoRecipient recipient;

    @BeforeEach
    void setUp() {
        service = new NotificationService(adminRepository, recipientRepository,
                templateFactoryResolver, recorder, notificationSender, recipientStatusService,
                notificationRepository, textTemplateFactory);
        Admin admin = mock(Admin.class);
        recipient = mock(UserKakaoRecipient.class);
        when(adminRepository.findByLoginId("admin")).thenReturn(Optional.of(admin));
        when(admin.getId()).thenReturn(6L);
        when(admin.getKakaoSenderConnectionId()).thenReturn(1L);
        when(recipientRepository.findByUserId(2L)).thenReturn(Optional.of(recipient));
        when(recipient.getStatus()).thenReturn(KakaoRecipientStatus.READY);
        when(recipient.getKakaoMessageUuid()).thenReturn("uuid");
        when(recipient.getId()).thenReturn(4L);
        when(templateFactoryResolver.create(KakaoTemplateType.TEXT, command))
                .thenReturn(new CreatedKakaoTemplate(template, "본문"));
        when(recorder.createRequested("SELECTION_APPROVED", 3L, "uuid", "본문",
                NotificationInitiatorType.ADMIN, 6L))
                .thenReturn(5L);
    }

    @Test
    @DisplayName("수신자 UUID 오류일 때만 재인증 필요 상태로 변경한다")
    void markReauthRequiredForInvalidReceiver() {
        doThrow(new BusinessException(ErrorCode.KAKAO_RECIPIENT_INVALID))
                .when(notificationSender).sendToFriend(1L, "uuid", template);

        assertThatThrownBy(() -> service.sendToFriend("admin", command))
                .isInstanceOf(BusinessException.class);

        verify(recipientStatusService).markReauthRequired(4L);
        verify(recorder).markFailed(5L);
    }

    @Test
    @DisplayName("일반 발송 실패에는 수신자 상태를 변경하지 않는다")
    void keepRecipientStatusForGeneralFailure() {
        doThrow(new BusinessException(ErrorCode.KAKAO_MESSAGE_SEND_FAILED))
                .when(notificationSender).sendToFriend(1L, "uuid", template);

        assertThatThrownBy(() -> service.sendToFriend("admin", command))
                .isInstanceOf(BusinessException.class);

        verify(recipientStatusService, never()).markReauthRequired(4L);
        verify(recorder).markFailed(5L);
    }

    @Test
    @DisplayName("실패한 메시지는 저장된 원문으로 재발송하고 원본 이력을 발송 완료로 갱신한다")
    void resendFailedMessageUpdatesOriginalNotification() {
        Notification notification = mock(Notification.class);
        when(notificationRepository.findById(5L)).thenReturn(Optional.of(notification));
        when(notification.getStatus()).thenReturn(NotificationStatus.FAILED);
        when(notification.getNotificationChannel()).thenReturn(NotificationChannel.KAKAO_MESSAGE);
        when(notification.getReceiver()).thenReturn("uuid");
        when(notification.getBody()).thenReturn("저장된 원문");
        when(notification.getId()).thenReturn(5L);
        when(recipientRepository.findByKakaoMessageUuid("uuid")).thenReturn(Optional.of(recipient));
        when(textTemplateFactory.createReplayTemplate("저장된 원문")).thenReturn(template);

        service.resendFailed("admin", 5L);

        verify(notificationSender).sendToFriend(1L, "uuid", template);
        verify(notification).markSent(org.mockito.ArgumentMatchers.any());
        verify(notificationRepository).save(notification);
    }

    @Test
    @DisplayName("발송 실패 상태가 아닌 이력은 재발송하지 않는다")
    void rejectResendForNonFailedNotification() {
        Notification notification = mock(Notification.class);
        when(notificationRepository.findById(5L)).thenReturn(Optional.of(notification));
        when(notification.getStatus()).thenReturn(NotificationStatus.SENT);

        assertThatThrownBy(() -> service.resendFailed("admin", 5L))
                .isInstanceOf(BusinessException.class);

        verify(notificationSender, never()).sendToFriend(
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("이메일 발송 실패 이력은 카카오 메시지로 재발송하지 않는다")
    void rejectResendForEmailNotification() {
        Notification notification = mock(Notification.class);
        when(notificationRepository.findById(5L)).thenReturn(Optional.of(notification));
        when(notification.getStatus()).thenReturn(NotificationStatus.FAILED);
        when(notification.getNotificationChannel()).thenReturn(NotificationChannel.EMAIL);

        assertThatThrownBy(() -> service.resendFailed("admin", 5L))
                .isInstanceOf(BusinessException.class);

        verify(notificationSender, never()).sendToFriend(
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any());
    }
}
