package com.fuma.hiselectors.selectors.model;

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
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import jakarta.persistence.EntityListeners;

/**
 * 셀렉터스가 어느 기수에 속했는지 기록한다. 한 셀렉터스가 여러 기수에 참여할 수 있다.
 *
 * <p><b>{@link com.fuma.hiselectors.common.BaseTimeEntity} 를 상속하지 않는다.</b>
 * 기존 테이블에 {@code updated_at} 컬럼이 없고, 참여 이력은 한 번 쌓이면 바뀌지 않는다.
 */
@Entity
@Table(name = "selectors_generation")
@EntityListeners(AuditingEntityListener.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SelectorsGeneration {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "selectors_generation_id")
    private Long id;

    @Column(name = "selectors_id", nullable = false)
    private Long selectorsId;

    @Column(name = "generation_id", nullable = false)
    private Long generationId;

    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Builder
    private SelectorsGeneration(Long selectorsId, Long generationId) {
        this.selectorsId = selectorsId;
        this.generationId = generationId;
    }
}
