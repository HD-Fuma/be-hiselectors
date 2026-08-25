package com.fuma.hiselectors.inspection.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fuma.hiselectors.inspection.model.ViolationStatus;
import com.fuma.hiselectors.inspection.service.ViolationConfirmationWriter.ConfirmationPreparation;
import com.fuma.hiselectors.notification.dto.NotificationSendResponse;
import com.fuma.hiselectors.notification.model.NotificationStatus;
import com.fuma.hiselectors.notification.service.NotificationService;
import org.junit.jupiter.api.Test;

class ViolationAdminServiceTest {

    @Test
    void sendsEditRequestAndMarksViolationAfterSuccessfulNotification() {
        ViolationConfirmationWriter writer = mock(ViolationConfirmationWriter.class);
        NotificationService notificationService = mock(NotificationService.class);
        when(writer.prepare(10L, "admin")).thenReturn(new ConfirmationPreparation(
                10L, 7L, "셀렉터", "광고 문구 누락", true, false));
        when(notificationService.sendToFriend(any(), any()))
                .thenReturn(new NotificationSendResponse(99L, NotificationStatus.SENT));
        when(writer.markEditRequested(10L)).thenReturn(ViolationStatus.EDIT_REQUESTED);
        ViolationAdminService service = new ViolationAdminService(writer, notificationService);

        var response = service.confirm(10L, "admin");

        assertThat(response.status()).isEqualTo(ViolationStatus.EDIT_REQUESTED);
        assertThat(response.penaltyCreated()).isTrue();
        assertThat(response.notificationId()).isEqualTo(99L);
    }

    @Test
    void doesNotResendAlreadyRequestedViolation() {
        ViolationConfirmationWriter writer = mock(ViolationConfirmationWriter.class);
        NotificationService notificationService = mock(NotificationService.class);
        when(writer.prepare(10L, "admin")).thenReturn(new ConfirmationPreparation(
                10L, 7L, "셀렉터", "광고 문구 누락", false, true));
        ViolationAdminService service = new ViolationAdminService(writer, notificationService);

        var response = service.confirm(10L, "admin");

        assertThat(response.status()).isEqualTo(ViolationStatus.EDIT_REQUESTED);
        verify(notificationService, never()).sendToFriend(any(), any());
    }
}
