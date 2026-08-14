package com.fuma.hiselectors.notification.model;

import com.fuma.hiselectors.common.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Objects;

@Entity
@Table(name = "notification")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Notification extends BaseTimeEntity {

    private static final int MAX_BODY_LENGTH = 400;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "notification_id")
    private Long id;

    @Column(name = "notification_purpose_code", nullable = false, length = 20)
    private String notificationPurposeCode;

    @Column(name = "reference_id")
    private Long referenceId;

    @Enumerated(EnumType.STRING)
    @Column(name = "notification_channel", nullable = false, length = 30)
    private NotificationChannel notificationChannel;

    @Column(name = "receiver", nullable = false, length = 255)
    private String receiver;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 50)
    private NotificationStatus status;

    @Column(name = "body", nullable = false, length = MAX_BODY_LENGTH)
    private String body;

    @Column(name = "request_at", nullable = false)
    private LocalDateTime requestAt;

    @Column(name = "sent_at")
    private LocalDateTime sentAt;

    @Builder
    private Notification(String notificationPurposeCode,
                         Long referenceId,
                         NotificationChannel notificationChannel,
                         String receiver,
                         String body,
                         LocalDateTime requestAt) {
        this.notificationPurposeCode = Objects.requireNonNull(notificationPurposeCode);
        this.referenceId = referenceId;
        this.notificationChannel = Objects.requireNonNull(notificationChannel);
        this.receiver = Objects.requireNonNull(receiver);
        this.body = validateBody(body);
        this.status = NotificationStatus.REQUESTED;
        this.requestAt = requestAt == null ? LocalDateTime.now() : requestAt;
    }

    public void markSent(LocalDateTime sentAt) {
        this.status = NotificationStatus.SENT;
        this.sentAt = Objects.requireNonNull(sentAt);
    }

    public void markFailed() {
        this.status = NotificationStatus.FAILED;
        this.sentAt = null;
    }

    private String validateBody(String body) {
        Objects.requireNonNull(body);
        if (body.length() > MAX_BODY_LENGTH) {
            throw new IllegalArgumentException("알림 본문은 400자를 초과할 수 없습니다.");
        }
        return body;
    }
}
