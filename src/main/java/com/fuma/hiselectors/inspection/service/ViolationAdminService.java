package com.fuma.hiselectors.inspection.service;

import com.fuma.hiselectors.inspection.dto.ViolationActionResponse;
import com.fuma.hiselectors.inspection.model.ViolationStatus;
import com.fuma.hiselectors.inspection.service.ViolationConfirmationWriter.ConfirmationPreparation;
import com.fuma.hiselectors.notification.dto.NotificationMessageCommand;
import com.fuma.hiselectors.notification.dto.NotificationSendResponse;
import com.fuma.hiselectors.notification.model.NotificationType;
import com.fuma.hiselectors.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ViolationAdminService {

    private static final int MAX_REASON_LENGTH = 35;

    private final ViolationConfirmationWriter confirmationWriter;
    private final NotificationService notificationService;

    public ViolationActionResponse confirm(Long violationId, String adminLoginId) {
        ConfirmationPreparation preparation = confirmationWriter.prepare(
                violationId, adminLoginId);
        if (preparation.alreadyRequested()) {
            return new ViolationActionResponse(
                    violationId, ViolationStatus.EDIT_REQUESTED, false, null);
        }

        NotificationSendResponse notification = notificationService.sendToFriend(
                adminLoginId,
                new NotificationMessageCommand(
                        null,
                        preparation.recipientUserId(),
                        preparation.violationId(),
                        preparation.receiverName(),
                        messageDetail(preparation),
                        NotificationType.CONTENT_EDIT_REQUEST));
        ViolationStatus status = confirmationWriter.markEditRequested(violationId);
        return new ViolationActionResponse(
                violationId, status, preparation.penaltyCreated(), notification.notificationId());
    }

    public ViolationActionResponse dismiss(Long violationId) {
        ViolationStatus status = confirmationWriter.dismiss(violationId);
        return new ViolationActionResponse(violationId, status, false, null);
    }

    private String messageDetail(ConfirmationPreparation preparation) {
        String reason = preparation.reason() == null ? "수정이 필요한 사항이 확인되었습니다."
                : truncate(preparation.reason(), MAX_REASON_LENGTH);
        String detail = "위반 사항: " + reason;
        if (preparation.penaltyCreated()) {
            detail += "\n패널티가 적용되었습니다.";
        }
        return detail;
    }

    private String truncate(String value, int maxLength) {
        return value.length() <= maxLength ? value : value.substring(0, maxLength - 1) + "…";
    }
}
