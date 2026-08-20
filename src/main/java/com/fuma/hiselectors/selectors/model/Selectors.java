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

    /**
     * 탈퇴·제명된 셀렉터스. 행을 지우지 않고 표시만 한다.
     *
     * <p>{@code selectors} 는 {@code selectors_generation},
     * {@code selectors_sns_account} 등이 참조하는 중심 테이블이라 물리 삭제할 수 없다.
     * 조회 API 는 항상 이 값이 false 인 행만 돌려준다.
     */
    @Column(name = "is_deleted", nullable = false)
    private boolean deleted;

    @Builder
    private Selectors(Long applicationId, Long userId, String selectorsRoleId,
                      String selectorsCode, String selectorsNickname) {
        this.applicationId = applicationId;
        this.userId = userId;
        this.selectorsRoleId = selectorsRoleId;
        this.selectorsCode = selectorsCode;
        this.selectorsNickname = selectorsNickname;
        this.deleted = false;
    }

    public void softDelete() {
        this.deleted = true;
    }

    public void restore() {
        this.deleted = false;
    }
}
