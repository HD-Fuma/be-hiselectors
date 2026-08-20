package com.fuma.hiselectors.notification.dto;

import com.fuma.hiselectors.kakao.model.KakaoRecipientStatus;
import com.fuma.hiselectors.notification.model.NotificationChannel;
import com.fuma.hiselectors.notification.model.NotificationStatus;
import java.time.LocalDateTime;

public record NotificationHistoryResponse(
        Long notificationId,
        String purposeCode,
        NotificationChannel channel,
        NotificationStatus status,
        String receiver,
        String body,
        Long referenceId,
        LocalDateTime requestAt,
        LocalDateTime sentAt,
        Long recipientUserId,
        String recipientName,
        String recipientHiId,
        KakaoRecipientStatus recipientStatus
) {
}
