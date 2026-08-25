package com.fuma.hiselectors.content.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fuma.hiselectors.notification.dto.NotificationMessageCommand;
import com.fuma.hiselectors.notification.model.NotificationType;
import com.fuma.hiselectors.notification.service.NotificationService;
import com.fuma.hiselectors.selectors.model.Selectors;
import com.fuma.hiselectors.selectors.repository.SelectorsRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

class ContentViolationNotificationListenerTest {

    @Test
    void sendsContentEditRequestToContentOwner() {
        SelectorsRepository selectorsRepository = mock(SelectorsRepository.class);
        NotificationService notificationService = mock(NotificationService.class);
        Selectors selectors = Selectors.builder()
                .userId(7L)
                .selectorsRoleId(Selectors.ACTIVE_ROLE)
                .selectorsNickname("selector")
                .build();
        ReflectionTestUtils.setField(selectors, "id", 5L);
        when(selectorsRepository.findById(5L)).thenReturn(Optional.of(selectors));
        ContentViolationNotificationListener listener =
                new ContentViolationNotificationListener(selectorsRepository, notificationService);

        listener.notifyEditRequest(new ContentViolationConfirmedEvent("admin", 10L, 5L));

        ArgumentCaptor<NotificationMessageCommand> commandCaptor =
                ArgumentCaptor.forClass(NotificationMessageCommand.class);
        verify(notificationService).sendToFriend(eq("admin"), commandCaptor.capture());
        NotificationMessageCommand command = commandCaptor.getValue();
        assertThat(command.recipientUserId()).isEqualTo(7L);
        assertThat(command.referenceId()).isEqualTo(10L);
        assertThat(command.receiverName()).isEqualTo("selector");
        assertThat(command.notificationType()).isEqualTo(NotificationType.CONTENT_EDIT_REQUEST);
    }
}
