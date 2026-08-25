package com.fuma.hiselectors.content.service;

import com.fuma.hiselectors.notification.dto.NotificationMessageCommand;
import com.fuma.hiselectors.notification.model.NotificationType;
import com.fuma.hiselectors.notification.service.NotificationService;
import com.fuma.hiselectors.selectors.model.Selectors;
import com.fuma.hiselectors.selectors.repository.SelectorsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class ContentViolationNotificationListener {

    private final SelectorsRepository selectorsRepository;
    private final NotificationService notificationService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void notifyEditRequest(ContentViolationConfirmedEvent event) {
        try {
            Selectors selectors = selectorsRepository.findById(event.selectorsId()).orElse(null);
            if (selectors == null || selectors.getUserId() == null) {
                log.warn("Content violation notification skipped: contentId={}, selectorsId={}",
                        event.contentId(), event.selectorsId());
                return;
            }
            notificationService.sendToFriend(event.adminLoginId(),
                    new NotificationMessageCommand(
                            null,
                            selectors.getUserId(),
                            event.contentId(),
                            selectors.getSelectorsNickname(),
                            null,
                            NotificationType.CONTENT_EDIT_REQUEST));
        } catch (RuntimeException exception) {
            log.warn("Content violation notification failed: contentId={}, selectorsId={}",
                    event.contentId(), event.selectorsId(), exception);
        }
    }
}
