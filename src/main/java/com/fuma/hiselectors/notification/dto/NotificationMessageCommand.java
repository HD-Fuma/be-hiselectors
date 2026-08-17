package com.fuma.hiselectors.notification.dto;

import com.fuma.hiselectors.notification.model.NotificationType;

public record NotificationMessageCommand(
        Long senderAdminId,
        Long recipientUserId,
        Long referenceId,
        String receiverName,
        String detail,
        NotificationType notificationType
) {
}
