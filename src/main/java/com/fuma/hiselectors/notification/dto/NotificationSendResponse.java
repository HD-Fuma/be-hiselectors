package com.fuma.hiselectors.notification.dto;

import com.fuma.hiselectors.notification.model.NotificationStatus;

public record NotificationSendResponse(Long notificationId, NotificationStatus status) {
}
