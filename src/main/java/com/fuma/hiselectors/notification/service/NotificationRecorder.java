package com.fuma.hiselectors.notification.service;

import com.fuma.hiselectors.exception.BusinessException;
import com.fuma.hiselectors.exception.ErrorCode;
import com.fuma.hiselectors.notification.model.Notification;
import com.fuma.hiselectors.notification.model.NotificationChannel;
import com.fuma.hiselectors.notification.model.NotificationInitiatorType;
import com.fuma.hiselectors.notification.repository.NotificationRepository;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class NotificationRecorder {

    private final NotificationRepository notificationRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Long createRequested(String purposeCode, Long referenceId,
                                String receiver, String body) {
        return createRequested(purposeCode, referenceId, receiver, body,
                NotificationInitiatorType.SYSTEM, null);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Long createRequested(String purposeCode, Long referenceId,
                                String receiver, String body,
                                NotificationInitiatorType initiatedByType,
                                Long initiatedById) {
        Notification notification = Notification.builder()
                .notificationPurposeCode(purposeCode)
                .referenceId(referenceId)
                .initiatedByType(initiatedByType)
                .initiatedById(initiatedById)
                .notificationChannel(NotificationChannel.KAKAO_MESSAGE)
                .receiver(receiver)
                .body(body)
                .requestAt(LocalDateTime.now())
                .build();
        return notificationRepository.save(notification).getId();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markSent(Long notificationId) {
        notificationRepository.findById(notificationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND))
                .markSent(LocalDateTime.now());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailed(Long notificationId) {
        notificationRepository.findById(notificationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND))
                .markFailed();
    }
}
