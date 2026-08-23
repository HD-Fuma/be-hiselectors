package com.fuma.hiselectors.notification.task;

import com.fuma.hiselectors.notification.service.NotificationService;
import com.fuma.hiselectors.taskrun.service.TrackedTask;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class KakaoMessageSendTask {

    private final NotificationService notificationService;

    public TrackedTask resend(String adminLoginId, Long notificationId) {
        return context -> {
            context.progress().start("KAKAO_MESSAGE_RESEND", 1);
            try {
                notificationService.resendFailed(adminLoginId, notificationId);
                context.progress().advance(1, 0, 0);
            } catch (RuntimeException exception) {
                context.progress().advance(0, 1, 0);
                throw exception;
            }
        };
    }
}
