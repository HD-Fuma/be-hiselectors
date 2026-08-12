package com.fuma.hiselectors.category.model;

import com.fuma.hiselectors.common.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 실제 YouTube 검색에 넣을 발굴 키워드.
 *
 * <p>{@code lastRunAt} 이 필요한 이유: YouTube Data API 의 {@code search.list} 는
 * 호출 1회에 100 units 를 쓰고 일일 한도가 10,000 units 다. 하루에 돌릴 수 있는
 * 키워드가 약 98개로 정해져 있어서, 같은 키워드를 매일 돌리면 쿼터만 태운다.
 * 오래 안 돌린 키워드부터 골라 돌리기 위해 마지막 실행 시각을 남긴다.
 *
 * <p>{@code priority} 는 키워드가 많아졌을 때 순서를 정하는 용도로 미리 넣어둔다.
 */
@Entity
@Table(
        name = "discovery_keyword",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_keyword_category", columnNames = {"category_id", "keyword"})
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DiscoveryKeyword extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "keyword_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    @Column(nullable = false, length = 100)
    private String keyword;

    @Column(nullable = false)
    private boolean enabled;

    /** 발굴 스케줄 우선순위. 클수록 먼저 돌린다. */
    @Column(nullable = false)
    private int priority;

    /** 마지막으로 이 키워드로 발굴을 돌린 시각. 한 번도 안 돌렸으면 null. */
    @Column(name = "last_run_at")
    private LocalDateTime lastRunAt;

    @Builder
    private DiscoveryKeyword(Category category, String keyword,
                             Integer priority, Boolean enabled) {
        this.category = category;
        this.keyword = keyword;
        this.priority = priority == null ? 0 : priority;
        this.enabled = enabled == null || enabled;
    }

    /** null 인 필드는 변경하지 않는다 (부분 수정). */
    public void update(Boolean enabled, Integer priority) {
        if (enabled != null) {
            this.enabled = enabled;
        }
        if (priority != null) {
            this.priority = priority;
        }
    }

    /** 발굴 배치가 이 키워드를 돌린 뒤 호출한다. */
    public void markRun(LocalDateTime runAt) {
        this.lastRunAt = runAt;
    }
}