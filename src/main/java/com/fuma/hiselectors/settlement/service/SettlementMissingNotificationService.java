package com.fuma.hiselectors.settlement.service;

import com.fuma.hiselectors.exception.BusinessException;
import com.fuma.hiselectors.exception.ErrorCode;
import com.fuma.hiselectors.notification.dto.NotificationMessageCommand;
import com.fuma.hiselectors.notification.model.NotificationType;
import com.fuma.hiselectors.notification.service.NotificationRecorder;
import com.fuma.hiselectors.notification.service.NotificationService;
import com.fuma.hiselectors.selectors.model.Selectors;
import com.fuma.hiselectors.selectors.repository.SelectorsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/** 정산정보 미기재 Hold가 처음 발생했을 때만 실행하는 최선형(best-effort) 알림 발송기. */
@Slf4j
@Service
@RequiredArgsConstructor
public class SettlementMissingNotificationService {

    private static final String FAILURE_BODY = "정산 계좌정보가 등록되지 않아 지급이 보류되었습니다.";

    @Value("${settlement.notification.sender-admin-login-id:}")
    private String senderAdminLoginId;

    private final SelectorsRepository selectorsRepository;
    private final NotificationService notificationService;
    private final NotificationRecorder notificationRecorder;

    public void notifyMissing(Long settlementId, Long selectorsId) {
        if (senderAdminLoginId == null || senderAdminLoginId.isBlank()) {
            log.warn("정산정보 미기재 알림을 건너뜁니다: 발신 관리자 설정 없음, settlementId={}",
                    settlementId);
            recordFailure(settlementId, selectorsId, null);
            return;
        }
        Selectors selectors = selectorsRepository.findById(selectorsId).orElse(null);
        if (selectors == null || selectors.getUserId() == null) {
            log.warn("정산정보 미기재 알림을 건너뜁니다: 수신 셀렉터스 없음, settlementId={}",
                    settlementId);
            recordFailure(settlementId, selectorsId, selectors);
            return;
        }
        try {
            notificationService.sendToFriend(senderAdminLoginId,
                    new NotificationMessageCommand(
                            null,
                            selectors.getUserId(),
                            settlementId,
                            selectors.getSelectorsNickname(),
                            null,
                            NotificationType.SETTLEMENT_MISSING));
        } catch (RuntimeException e) {
            log.warn("정산정보 미기재 알림 발송 실패: settlementId={}, selectorsId={}",
                    settlementId, selectorsId, e);
            if (shouldRecordFallbackFailure(e)) {
                recordFailure(settlementId, selectorsId, selectors);
            }
        }
    }

    private boolean shouldRecordFallbackFailure(RuntimeException exception) {
        if (!(exception instanceof BusinessException businessException)) {
            return false;
        }
        return businessException.getErrorCode() == ErrorCode.UNAUTHORIZED
                || businessException.getErrorCode() == ErrorCode.KAKAO_SENDER_NOT_CONNECTED
                || businessException.getErrorCode() == ErrorCode.KAKAO_RECIPIENT_NOT_FOUND
                || businessException.getErrorCode() == ErrorCode.KAKAO_RECIPIENT_NOT_READY;
    }

    private void recordFailure(Long settlementId, Long selectorsId, Selectors selectors) {
        String receiver = selectors != null && selectors.getUserId() != null
                ? "USER:" + selectors.getUserId()
                : "SELECTORS:" + selectorsId;
        try {
            Long notificationId = notificationRecorder.createRequested(
                    NotificationType.SETTLEMENT_MISSING.getPurposeCode(), settlementId,
                    receiver, FAILURE_BODY);
            notificationRecorder.markFailed(notificationId);
        } catch (RuntimeException e) {
            log.warn("정산정보 미기재 알림 실패 이력 기록 실패: settlementId={}", settlementId, e);
        }
    }
}
