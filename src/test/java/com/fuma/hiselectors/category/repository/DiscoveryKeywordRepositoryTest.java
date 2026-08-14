package com.fuma.hiselectors.category.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.fuma.hiselectors.category.model.Category;
import com.fuma.hiselectors.category.model.DiscoveryKeyword;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.test.context.TestPropertySource;

@DataJpaTest
@TestPropertySource(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
class DiscoveryKeywordRepositoryTest {

    @Autowired
    private DiscoveryKeywordRepository keywordRepository;

    @Autowired
    private TestEntityManager em;

    @Test
    @DisplayName("활성 키워드를 우선순위와 마지막 실행 시각 순으로 조회한다")
    void findRunnableKeywordsInExecutionOrder() {
        Category activeCategory = persistCategory("BEAUTY", "뷰티", true);
        DiscoveryKeyword highNever = persistKeyword(activeCategory, "겟레디윗미", 10, true);
        DiscoveryKeyword highOld = persistKeyword(activeCategory, "메이크업", 10, true);
        highOld.markRun(LocalDateTime.now().minusDays(3));
        persistKeyword(activeCategory, "비활성 키워드", 100, false);
        persistKeyword(activeCategory, "패션하울", 5, true);

        Category inactiveCategory = persistCategory("FITNESS", "피트니스", false);
        persistKeyword(inactiveCategory, "러닝", 100, true);

        em.flush();
        em.clear();

        List<DiscoveryKeyword> result = keywordRepository.findRunnable();

        assertThat(result)
                .extracting(DiscoveryKeyword::getKeyword)
                .containsExactly("겟레디윗미", "메이크업", "패션하울");
        assertThat(result)
                .extracting(keyword -> keyword.getCategory().getCode())
                .containsOnly("BEAUTY");
    }

    private Category persistCategory(String code, String name, boolean enabled) {
        Category category = Category.builder()
                .code(code)
                .name(name)
                .enabled(enabled)
                .build();
        em.persist(category);
        return category;
    }

    private DiscoveryKeyword persistKeyword(
            Category category, String keyword, int priority, boolean enabled) {
        DiscoveryKeyword created = category.addKeyword(keyword, priority);
        created.update(enabled, null);
        em.persist(created);
        return created;
    }
}
