package com.fuma.hiselectors.performance.notification;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fuma.hiselectors.notification.dto.NotificationMessageCommand;
import com.fuma.hiselectors.notification.model.NotificationType;
import com.fuma.hiselectors.notification.repository.NotificationRepository;
import com.fuma.hiselectors.notification.service.NotificationService;
import com.fuma.hiselectors.selectors.model.Selectors;
import com.fuma.hiselectors.selectors.repository.SelectorsRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

class PerformanceNotificationServiceTest {

    private SelectorsRepository selectorsRepository;
    private NotificationRepository notificationRepository;
    private NotificationService notificationService;
    private PerformanceNotificationService service;
    private Selectors selectors;

    @BeforeEach
    void setUp() {
        selectorsRepository = mock(SelectorsRepository.class);
        notificationRepository = mock(NotificationRepository.class);
        notificationService = mock(NotificationService.class);
        service = new PerformanceNotificationService(
                selectorsRepository, notificationRepository, notificationService);
        ReflectionTestUtils.setField(service, "senderAdminLoginId", "sender-admin");

        selectors = mock(Selectors.class);
        when(selectors.getId()).thenReturn(2L);
        when(selectors.getUserId()).thenReturn(20L);
        when(selectors.getSelectorsNickname()).thenReturn("셀렉터");
        when(selectorsRepository.findByIdForUpdate(2L)).thenReturn(Optional.of(selectors));
    }

    @Test
    void sendsFirstPurchaseNotificationOnce() {
        service.handlePurchaseCreated(new PurchaseCreatedEvent(101L, 2L));

        ArgumentCaptor<NotificationMessageCommand> commandCaptor =
                ArgumentCaptor.forClass(NotificationMessageCommand.class);
        verify(notificationService).sendToFriend(eq("sender-admin"), commandCaptor.capture());
        NotificationMessageCommand command = commandCaptor.getValue();
        org.assertj.core.api.Assertions.assertThat(command.recipientUserId()).isEqualTo(20L);
        org.assertj.core.api.Assertions.assertThat(command.referenceId()).isEqualTo(2L);
        org.assertj.core.api.Assertions.assertThat(command.receiverName()).isEqualTo("셀렉터");
        org.assertj.core.api.Assertions.assertThat(command.notificationType())
                .isEqualTo(NotificationType.FIRST_PURCHASE);
    }

    @Test
    void sendsOnlyOnceWhenPurchaseEventIsRepeated() {
        when(notificationRepository.countByNotificationPurposeCodeAndReferenceId(
                NotificationType.FIRST_PURCHASE.getPurposeCode(), 2L))
                .thenReturn(0L, 1L);

        service.handlePurchaseCreated(new PurchaseCreatedEvent(101L, 2L));
        service.handlePurchaseCreated(new PurchaseCreatedEvent(102L, 2L));

        verify(notificationService).sendToFriend(any(), any());
    }

    @Test
    void skipsWhenSenderIsNotConfigured() {
        ReflectionTestUtils.setField(service, "senderAdminLoginId", " ");

        service.handlePurchaseCreated(new PurchaseCreatedEvent(101L, 2L));

        verify(selectorsRepository, never()).findByIdForUpdate(any());
        verify(notificationService, never()).sendToFriend(any(), any());
    }

    @Test
    void doesNotPropagateKakaoFailure() {
        when(notificationService.sendToFriend(any(), any()))
                .thenThrow(new IllegalStateException("kakao failed"));

        assertThatCode(() -> service.handlePurchaseCreated(new PurchaseCreatedEvent(101L, 2L)))
                .doesNotThrowAnyException();
    }
}
