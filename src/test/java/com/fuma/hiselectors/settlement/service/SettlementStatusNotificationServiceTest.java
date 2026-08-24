package com.fuma.hiselectors.settlement.service;

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
import com.fuma.hiselectors.settlement.model.SettlementHistory;
import com.fuma.hiselectors.settlement.model.SettlementStatus;
import com.fuma.hiselectors.settlement.repository.SettlementHistoryRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

class SettlementStatusNotificationServiceTest {

    private SettlementHistoryRepository settlementHistoryRepository;
    private SelectorsRepository selectorsRepository;
    private NotificationRepository notificationRepository;
    private NotificationService notificationService;
    private SettlementStatusNotificationService service;
    private SettlementHistory history;

    @BeforeEach
    void setUp() {
        settlementHistoryRepository = mock(SettlementHistoryRepository.class);
        selectorsRepository = mock(SelectorsRepository.class);
        notificationRepository = mock(NotificationRepository.class);
        notificationService = mock(NotificationService.class);
        service = new SettlementStatusNotificationService(
                settlementHistoryRepository, selectorsRepository,
                notificationRepository, notificationService);
        ReflectionTestUtils.setField(service, "senderAdminLoginId", "sender-admin");

        history = SettlementHistory.create(
                2L, LocalDateTime.of(2026, 6, 1, 0, 0));
        ReflectionTestUtils.setField(history, "id", 10L);
        ReflectionTestUtils.setField(history, "settlementAmount", 84_000L);
        ReflectionTestUtils.setField(history, "status", SettlementStatus.PAYMENT_PENDING);
        when(settlementHistoryRepository.findByIdForUpdate(10L))
                .thenReturn(Optional.of(history));
        Selectors selectors = mock(Selectors.class);
        when(selectors.getUserId()).thenReturn(20L);
        when(selectors.getSelectorsNickname()).thenReturn("셀렉터스");
        when(selectorsRepository.findById(2L)).thenReturn(Optional.of(selectors));
    }

    @Test
    void sendsUpcomingSettlementOnce() {
        service.notifyUpcoming(10L, LocalDate.of(2026, 8, 20));

        ArgumentCaptor<NotificationMessageCommand> commandCaptor =
                ArgumentCaptor.forClass(NotificationMessageCommand.class);
        verify(notificationService).sendToFriendAsSystem(eq("sender-admin"), commandCaptor.capture());
        NotificationMessageCommand command = commandCaptor.getValue();
        org.assertj.core.api.Assertions.assertThat(command.referenceId()).isEqualTo(10L);
        org.assertj.core.api.Assertions.assertThat(command.detail()).isEqualTo(
                "2026년 6월 정산금 84,000원이 2026년 8월 20일에 정산될 예정이에요.");
        org.assertj.core.api.Assertions.assertThat(command.notificationType())
                .isEqualTo(NotificationType.SETTLEMENT_UPCOMING);
    }

    @Test
    void skipsUpcomingSettlementWhenAlreadySent() {
        when(notificationRepository.countByNotificationPurposeCodeAndReferenceId(
                NotificationType.SETTLEMENT_UPCOMING.getPurposeCode(), 10L)).thenReturn(1L);

        service.notifyUpcoming(10L, LocalDate.of(2026, 8, 20));

        verify(notificationService, never()).sendToFriendAsSystem(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    void sendsCompletedSettlementOnce() {
        history.transitionTo(SettlementStatus.SETTLED,
                LocalDateTime.of(2026, 8, 20, 9, 0));

        service.notifyCompleted(10L);

        ArgumentCaptor<NotificationMessageCommand> commandCaptor =
                ArgumentCaptor.forClass(NotificationMessageCommand.class);
        verify(notificationService).sendToFriendAsSystem(eq("sender-admin"), commandCaptor.capture());
        NotificationMessageCommand command = commandCaptor.getValue();
        org.assertj.core.api.Assertions.assertThat(command.detail()).isEqualTo(
                "2026년 6월 정산금 84,000원의 정산 처리가 완료되었어요.");
        org.assertj.core.api.Assertions.assertThat(command.notificationType())
                .isEqualTo(NotificationType.SETTLEMENT_COMPLETED);
    }

    @Test
    void skipsCompletedSettlementWhenAlreadySent() {
        history.transitionTo(SettlementStatus.SETTLED,
                LocalDateTime.of(2026, 8, 20, 9, 0));
        when(notificationRepository.countByNotificationPurposeCodeAndReferenceId(
                NotificationType.SETTLEMENT_COMPLETED.getPurposeCode(), 10L)).thenReturn(1L);

        service.notifyCompleted(10L);

        verify(notificationService, never()).sendToFriendAsSystem(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
    }
}
