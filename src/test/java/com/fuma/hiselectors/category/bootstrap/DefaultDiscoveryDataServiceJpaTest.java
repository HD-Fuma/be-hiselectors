package com.fuma.hiselectors.category.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;

import com.fuma.hiselectors.category.bootstrap.DefaultDiscoveryDataService.InitializationResult;
import com.fuma.hiselectors.category.model.Category;
import com.fuma.hiselectors.category.repository.CategoryRepository;
import com.fuma.hiselectors.category.repository.DiscoveryKeywordRepository;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@DataJpaTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:default-discovery;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@Import({DefaultDiscoveryDataService.class, DefaultDiscoveryCategoryWriter.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class DefaultDiscoveryDataServiceJpaTest {

    @Autowired
    private DefaultDiscoveryDataService service;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private DiscoveryKeywordRepository keywordRepository;

    @AfterEach
    void cleanUp() {
        keywordRepository.deleteAllInBatch();
        categoryRepository.deleteAllInBatch();
    }

    @Test
    @DisplayName("기본 데이터 초기화를 두 번 실행해도 카테고리와 키워드가 중복되지 않는다")
    void initializeIdempotently() {
        InitializationResult first = service.initialize();
        InitializationResult second = service.initialize();

        assertThat(first.createdCategories()).isEqualTo(9);
        assertThat(first.createdKeywords()).isEqualTo(60);
        assertThat(categoryRepository.count()).isEqualTo(9);
        assertThat(keywordRepository.count()).isEqualTo(60);

        assertThat(second.createdCategories()).isZero();
        assertThat(second.createdKeywords()).isZero();
        assertThat(second.skippedCategories()).isEqualTo(9);
        assertThat(categoryRepository.count()).isEqualTo(9);
        assertThat(keywordRepository.count()).isEqualTo(60);
    }

    @Test
    @DisplayName("관리자가 등록한 키워드는 유지하고 나머지 기본 카테고리를 생성한다")
    void keepAdminConfiguredCategory() {
        Category beauty = Category.builder()
                .code("BEAUTY")
                .name("뷰티")
                .build();
        beauty.addKeyword("관리자 키워드", 100);
        categoryRepository.saveAndFlush(beauty);

        InitializationResult result = service.initialize();

        Category savedBeauty = findCategory("BEAUTY");
        assertThat(savedBeauty.getKeywords())
                .extracting(keyword -> keyword.getKeyword())
                .containsExactly("관리자 키워드");
        assertThat(result.createdCategories()).isEqualTo(8);
        assertThat(result.createdKeywords()).isEqualTo(52);
        assertThat(result.skippedCategories()).isEqualTo(1);
        assertThat(categoryRepository.count()).isEqualTo(9);
        assertThat(keywordRepository.count()).isEqualTo(53);
    }

    @Test
    @DisplayName("활성 카테고리에 키워드가 없으면 해당 카테고리의 기본 키워드를 채운다")
    void fillEmptyEnabledCategory() {
        categoryRepository.saveAndFlush(Category.builder()
                .code("BEAUTY")
                .name("뷰티")
                .enabled(true)
                .build());

        InitializationResult result = service.initialize();

        Category savedBeauty = findCategory("BEAUTY");
        assertThat(savedBeauty.getKeywords())
                .extracting(keyword -> keyword.getKeyword())
                .containsExactly(
                        "스킨케어", "메이크업", "바디케어", "헤어케어",
                        "프레그런스", "뷰티소품", "홈케어관리기", "남성화장품");
        assertThat(result.createdCategories()).isEqualTo(8);
        assertThat(result.createdKeywords()).isEqualTo(60);
        assertThat(result.skippedCategories()).isZero();
    }

    @Test
    @DisplayName("비활성 카테고리는 키워드가 비어 있어도 유지한다")
    void keepDisabledCategoryEmpty() {
        categoryRepository.saveAndFlush(Category.builder()
                .code("BEAUTY")
                .name("뷰티")
                .enabled(false)
                .build());

        InitializationResult result = service.initialize();

        assertThat(findCategory("BEAUTY").getKeywords()).isEmpty();
        assertThat(result.createdCategories()).isEqualTo(8);
        assertThat(result.createdKeywords()).isEqualTo(52);
        assertThat(result.skippedCategories()).isEqualTo(1);
    }

    private Category findCategory(String code) {
        List<Category> categories = categoryRepository.findAllByOrderByDisplayOrderAscIdAsc();
        return categories.stream()
                .filter(category -> category.getCode().equals(code))
                .findFirst()
                .orElseThrow();
    }
}
