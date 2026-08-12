package com.fuma.hiselectors.category.model;

import com.fuma.hiselectors.common.BaseTimeEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.util.ArrayList;
import java.util.List;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 크리에이터 발굴에 쓰는 대분류 카테고리. (뷰티, 패션, 헬스 …)
 *
 * <p>카테고리 아래에 실제 검색에 쓸 {@link DiscoveryKeyword} 를 여러 개 등록한다.
 * 예) 뷰티 → grwm, 겟레디윗미 / 헬스 → 러닝, 보디빌딩, 크로스핏
 */
@Entity
@Table(
        name = "category",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_category_code", columnNames = "category_code"),
                @UniqueConstraint(name = "uk_category_name", columnNames = "name")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Category extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "category_id")
    private Long id;

    /**
     * 카테고리 코드. UPPER_SNAKE_CASE. 예) {@code BEAUTY}
     *
     * <p>{@code creator_pool.category}, {@code creator_report.category},
     * {@code application_report.category} 가 varchar(20) 으로 담고 있는 값이 이것이다.
     * 기존 데이터가 참조하므로 등록 후에는 바꾸지 않는다.
     */
    @Column(name = "category_code", nullable = false, length = 20, updatable = false)
    private String code;

    /** 화면에 보여줄 이름. 예) 뷰티 */
    @Column(nullable = false, length = 50)
    private String name;

    /** 화면 노출 순서. 작을수록 앞. */
    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    /** 꺼두면 발굴 대상에서 제외된다. 삭제하지 않고 잠시 쉬게 할 때 쓴다. */
    @Column(nullable = false)
    private boolean enabled;

    @OneToMany(mappedBy = "category", cascade = CascadeType.ALL, orphanRemoval = true)
    private final List<DiscoveryKeyword> keywords = new ArrayList<>();

    // id는 DB auto_increment, createdAt/updatedAt은 Auditing이 채우므로 빌더에서 제외.
    @Builder
    private Category(String code, String name,
                     Integer displayOrder, Boolean enabled) {
        this.code = code;
        this.name = name;
        this.displayOrder = displayOrder == null ? 0 : displayOrder;
        this.enabled = enabled == null || enabled;
    }

    /** null 인 필드는 변경하지 않는다 (부분 수정). 코드는 바꿀 수 없다. */
    public void update(String name, Integer displayOrder, Boolean enabled) {
        if (name != null) {
            this.name = name;
        }
        if (displayOrder != null) {
            this.displayOrder = displayOrder;
        }
        if (enabled != null) {
            this.enabled = enabled;
        }
    }

    public DiscoveryKeyword addKeyword(String keyword, Integer priority) {
        DiscoveryKeyword created = DiscoveryKeyword.builder()
                .category(this)
                .keyword(keyword)
                .priority(priority)
                .build();
        this.keywords.add(created);
        return created;
    }

    /** orphanRemoval 로 실제 삭제까지 이어진다. */
    public void removeKeyword(DiscoveryKeyword keyword) {
        this.keywords.remove(keyword);
    }

    /** 같은 카테고리 안에서는 대소문자만 다른 키워드도 중복으로 본다. */
    public boolean hasKeyword(String keyword) {
        return this.keywords.stream()
                .anyMatch(k -> k.getKeyword().equalsIgnoreCase(keyword));
    }
}
