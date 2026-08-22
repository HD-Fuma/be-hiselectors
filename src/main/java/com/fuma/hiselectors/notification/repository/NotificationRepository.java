package com.fuma.hiselectors.notification.repository;

import com.fuma.hiselectors.notification.dto.NotificationHistoryResponse;
import com.fuma.hiselectors.notification.model.Notification;
import com.fuma.hiselectors.notification.model.NotificationStatus;
import java.time.LocalDateTime;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    long countByNotificationPurposeCodeAndReferenceId(String purposeCode, Long referenceId);

    @Query("""
            select count(notification)
            from Notification notification
            where notification.notificationPurposeCode = :purposeCode
              and notification.referenceId = :referenceId
              and notification.requestAt >= :startInclusive
              and notification.requestAt < :endExclusive
            """)
    long countByPurposeAndReferenceInPeriod(
            @Param("purposeCode") String purposeCode,
            @Param("referenceId") Long referenceId,
            @Param("startInclusive") LocalDateTime startInclusive,
            @Param("endExclusive") LocalDateTime endExclusive);

    @Query(
            value = """
                    select new com.fuma.hiselectors.notification.dto.NotificationHistoryResponse(
                        notification.id,
                        notification.notificationPurposeCode,
                        notification.notificationChannel,
                        notification.status,
                        notification.receiver,
                        notification.body,
                        notification.referenceId,
                        notification.requestAt,
                        notification.sentAt,
                        recipient.userId,
                        user.name,
                        user.hiId,
                        recipient.status
                    )
                    from Notification notification
                    left join UserKakaoRecipient recipient
                        on notification.receiver = recipient.kakaoMessageUuid
                    left join User user on recipient.userId = user.id
                    where (:purpose is null or notification.notificationPurposeCode = :purpose)
                      and (:status is null or notification.status = :status)
                      and (:fromAt is null or notification.requestAt >= :fromAt)
                      and (:toExclusive is null or notification.requestAt < :toExclusive)
                      and (
                          :recipientKeyword is null
                          or lower(user.name) like lower(concat('%', :recipientKeyword, '%'))
                          or lower(user.hiId) like lower(concat('%', :recipientKeyword, '%'))
                      )
                    """,
            countQuery = """
                    select count(notification)
                    from Notification notification
                    left join UserKakaoRecipient recipient
                        on notification.receiver = recipient.kakaoMessageUuid
                    left join User user on recipient.userId = user.id
                    where (:purpose is null or notification.notificationPurposeCode = :purpose)
                      and (:status is null or notification.status = :status)
                      and (:fromAt is null or notification.requestAt >= :fromAt)
                      and (:toExclusive is null or notification.requestAt < :toExclusive)
                      and (
                          :recipientKeyword is null
                          or lower(user.name) like lower(concat('%', :recipientKeyword, '%'))
                          or lower(user.hiId) like lower(concat('%', :recipientKeyword, '%'))
                      )
                    """)
    Page<NotificationHistoryResponse> searchHistory(
            @Param("purpose") String purpose,
            @Param("status") NotificationStatus status,
            @Param("fromAt") LocalDateTime fromAt,
            @Param("toExclusive") LocalDateTime toExclusive,
            @Param("recipientKeyword") String recipientKeyword,
            Pageable pageable);

}
