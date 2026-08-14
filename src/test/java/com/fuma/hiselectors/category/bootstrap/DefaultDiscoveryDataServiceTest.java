package com.fuma.hiselectors.category.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fuma.hiselectors.category.bootstrap.DefaultDiscoveryDataService.InitializationResult;
import com.fuma.hiselectors.category.model.Category;
import com.fuma.hiselectors.category.repository.CategoryRepository;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DefaultDiscoveryDataServiceTest {

    @Mock
    private CategoryRepository categoryRepository;

    @InjectMocks
    private DefaultDiscoveryDataService service;

    @Test
    @DisplayName("저장된 데이터가 없으면 기본 카테고리와 키워드를 생성한다")
    void initializeEmptyDatabase() {
        when(categoryRepository.findByCode(any())).thenReturn(Optional.empty());
        when(categoryRepository.findByName(any())).thenReturn(Optional.empty());
        when(categoryRepository.save(any(Category.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        InitializationResult result = service.initialize();

        assertThat(result.createdCategories()).isEqualTo(9);
        assertThat(result.createdKeywords()).isEqualTo(60);
        assertThat(result.skippedCategories()).isZero();

        ArgumentCaptor<Category> captor = ArgumentCaptor.forClass(Category.class);
        verify(categoryRepository, org.mockito.Mockito.times(9)).save(captor.capture());
        assertThat(captor.getAllValues())
                .extracting(Category::getCode)
                .containsExactly(
                        "BEAUTY", "FASHION", "FOOD", "LIVING_LIFE", "KIDS_FAMILY",
                        "CULTURE_SERVICE", "SPORTS_LEISURE", "TRAVEL", "PET_LIFE");
        assertThat(captor.getAllValues())
                .flatExtracting(Category::getKeywords)
                .hasSize(60);
    }

    @Test
    @DisplayName("관리자가 키워드를 등록한 카테고리는 변경하지 않는다")
    void keepAdminConfiguredCategory() {
        Category beauty = Category.builder()
                .code("BEAUTY")
                .name("뷰티")
                .build();
        beauty.addKeyword("관리자 키워드", 100);

        stubExistingCategory(beauty);
        when(categoryRepository.findByName(any())).thenReturn(Optional.of(beauty));

        InitializationResult result = service.initialize();

        assertThat(beauty.getKeywords())
                .extracting(keyword -> keyword.getKeyword())
                .containsExactly("관리자 키워드");
        assertThat(result.skippedCategories()).isEqualTo(9);
        verify(categoryRepository, never()).save(any());
    }

    @Test
    @DisplayName("활성 카테고리가 있지만 키워드가 비어 있으면 기본 키워드를 채운다")
    void fillEmptyEnabledCategory() {
        Category beauty = Category.builder()
                .code("BEAUTY")
                .name("뷰티")
                .enabled(true)
                .build();
        stubExistingCategory(beauty);
        when(categoryRepository.findByName(any())).thenReturn(Optional.of(beauty));

        InitializationResult result = service.initialize();

        assertThat(beauty.getKeywords())
                .extracting(keyword -> keyword.getKeyword())
                .containsExactly(
                        "스킨케어", "메이크업", "바디케어", "헤어케어",
                        "프레그런스", "뷰티소품", "홈케어관리기", "남성화장품");
        assertThat(result.createdCategories()).isZero();
        assertThat(result.createdKeywords()).isEqualTo(8);
        assertThat(result.skippedCategories()).isEqualTo(8);
        verify(categoryRepository).save(beauty);
    }

    @Test
    @DisplayName("비활성 카테고리에는 기본 키워드를 다시 추가하지 않는다")
    void keepDisabledCategoryEmpty() {
        Category beauty = Category.builder()
                .code("BEAUTY")
                .name("뷰티")
                .enabled(false)
                .build();
        stubExistingCategory(beauty);
        when(categoryRepository.findByName(any())).thenReturn(Optional.of(beauty));

        InitializationResult result = service.initialize();

        assertThat(beauty.getKeywords()).isEmpty();
        assertThat(result.skippedCategories()).isEqualTo(9);
        verify(categoryRepository, never()).save(any());
    }

    private void stubExistingCategory(Category category) {
        when(categoryRepository.findByCode(anyString())).thenAnswer(invocation ->
                category.getCode().equals(invocation.getArgument(0))
                        ? Optional.of(category)
                        : Optional.empty());
    }
}
