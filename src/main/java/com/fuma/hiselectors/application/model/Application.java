package com.fuma.hiselectors.application.model;

import com.fuma.hiselectors.common.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "application")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Application extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "application_id")
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "alarm_yn", nullable = false)
    private boolean alarmYn;

    @Column(name = "policy_agreed_at", nullable = false)
    private LocalDateTime policyAgreedAt;

    @Builder
    private Application(Long userId, boolean alarmYn, LocalDateTime policyAgreedAt) {
        this.userId = userId;
        this.alarmYn = alarmYn;
        this.policyAgreedAt = policyAgreedAt;
    }
}
