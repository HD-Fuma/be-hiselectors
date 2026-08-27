package com.fuma.hiselectors.penalty.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fuma.hiselectors.admin.model.Admin;
import com.fuma.hiselectors.admin.repository.AdminRepository;
import com.fuma.hiselectors.notification.dto.NotificationMessageCommand;
import com.fuma.hiselectors.notification.model.NotificationType;
import com.fuma.hiselectors.notification.service.NotificationService;
import com.fuma.hiselectors.selectors.model.Selectors;
import com.fuma.hiselectors.selectors.repository.SelectorsRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

class PenaltyReleasedNotificationListenerTest {

    @Test
    void sendsPenaltyReleasedNotificationToSelector() {
        AdminRepository adminRepository = mock(AdminRepository.class);
        SelectorsRepository selectorsRepository = mock(SelectorsRepository.class);
        NotificationService notificationService = mock(NotificationService.class);
        Admin admin = Admin.builder().loginId("admin").name("관리자").build();
        ReflectionTestUtils.setField(admin, "id", 3L);
        Selectors selectors = Selectors.builder()
                .userId(7L)
                .selectorsRoleId(Selectors.ACTIVE_ROLE)
                .selectorsNickname("셀렉터")
                .build();
        ReflectionTestUtils.setField(selectors, "id", 9L);
        when(adminRepository.findById(3L)).thenReturn(Optional.of(admin));
        when(selectorsRepository.findById(9L)).thenReturn(Optional.of(selectors));
        PenaltyReleasedNotificationListener listener =
                new PenaltyReleasedNotificationListener(
                        adminRepository, selectorsRepository, notificationService);

        listener.notifyPenaltyReleased(new PenaltyReleasedEvent(3L, 11L, 9L));

        ArgumentCaptor<NotificationMessageCommand> commandCaptor =
                ArgumentCaptor.forClass(NotificationMessageCommand.class);
        verify(notificationService).sendToFriend(eq("admin"), commandCaptor.capture());
        NotificationMessageCommand command = commandCaptor.getValue();
        assertThat(command.senderAdminId()).isEqualTo(3L);
        assertThat(command.recipientUserId()).isEqualTo(7L);
        assertThat(command.referenceId()).isEqualTo(11L);
        assertThat(command.receiverName()).isEqualTo("셀렉터");
        assertThat(command.notificationType()).isEqualTo(NotificationType.PENALTY_RELEASED);
    }
}
