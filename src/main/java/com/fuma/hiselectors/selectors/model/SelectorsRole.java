package com.fuma.hiselectors.selectors.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 셀렉터스 역할 코드 마스터. 현재 ACTIVE(활성), INACTIVE(비활성), BLACKLIST(블랙리스트).
 *
 * <p>운영 중 값이 늘어날 수 있어 enum 이 아니라 테이블로 둔다. 코드가 읽기 전용이므로
 * 생성·수정 메서드는 두지 않는다. {@code created_at} 컬럼이 없어 BaseTimeEntity 는 상속하지 않는다.
 */
@Entity
@Table(name = "selectors_role")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SelectorsRole {

    @Id
    @Column(name = "selectors_role_id", length = 20)
    private String id;

    @Column(name = "role_name", length = 20)
    private String roleName;
}
