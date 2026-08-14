package com.fuma.hiselectors.kakao.dto;

import com.fuma.hiselectors.notification.model.NotificationType;
import jakarta.validation.constraints.NotNull;

public record KakaoTestMessageRequest(
        Long userId,
        @NotNull NotificationType notificationType,
        Long referenceId,
        String receiverName,
        String detail
) {
}
