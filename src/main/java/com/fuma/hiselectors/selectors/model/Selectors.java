package com.fuma.hiselectors.selectors.model;

import com.fuma.hiselectors.common.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "selectors")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Selectors extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "selectors_id")
    private Long id;

    @Column(name = "application_id")
    private Long applicationId;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "selectors_role_id", nullable = false, length = 20)
    private String selectorsRoleId;

    @Column(name = "selectors_code", length = 20)
    private String selectorsCode;

    @Column(name = "selectors_nickname", length = 20)
    private String selectorsNickname;


    @Builder
    private Selectors(Long applicationId, Long userId, String selectorsRoleId,
                      String selectorsCode, String selectorsNickname) {
        this.applicationId = applicationId;
        this.userId = userId;
        this.selectorsRoleId = selectorsRoleId;
        this.selectorsCode = selectorsCode;
        this.selectorsNickname = selectorsNickname;
    }
}
