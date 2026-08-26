package com.fuma.hiselectors.penalty.service;

import com.fuma.hiselectors.admin.model.Admin;
import com.fuma.hiselectors.admin.repository.AdminRepository;
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
public class PenaltyReleasedNotificationListener {

    private final AdminRepository adminRepository;
    private final SelectorsRepository selectorsRepository;
    private final NotificationService notificationService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void notifyPenaltyReleased(PenaltyReleasedEvent event) {
        try {
            Admin sender = adminRepository.findById(event.senderAdminId()).orElse(null);
            Selectors selectors = selectorsRepository.findById(event.selectorsId()).orElse(null);
            if (sender == null || sender.getLoginId() == null
                    || selectors == null || selectors.getUserId() == null) {
                log.warn("Penalty release notification skipped: penaltyHistoryId={}, "
                                + "selectorsId={}, senderAdminId={}",
                        event.penaltyHistoryId(), event.selectorsId(), event.senderAdminId());
                return;
            }
            notificationService.sendToFriend(sender.getLoginId(),
                    new NotificationMessageCommand(
                            sender.getId(),
                            selectors.getUserId(),
                            event.penaltyHistoryId(),
                            selectors.getSelectorsNickname(),
                            null,
                            NotificationType.PENALTY_RELEASED));
        } catch (RuntimeException exception) {
            log.warn("Penalty release notification failed: penaltyHistoryId={}, selectorsId={}",
                    event.penaltyHistoryId(), event.selectorsId(), exception);
        }
    }
}
