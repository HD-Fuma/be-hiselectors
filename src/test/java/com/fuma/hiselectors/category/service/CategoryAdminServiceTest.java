package com.fuma.hiselectors.category.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fuma.hiselectors.category.dto.CategoryCreateRequest;
import com.fuma.hiselectors.category.dto.CategoryResponse;
import com.fuma.hiselectors.category.dto.KeywordCreateRequest;
import com.fuma.hiselectors.category.dto.KeywordCreateResponse;
import com.fuma.hiselectors.category.model.Category;
import com.fuma.hiselectors.category.model.DiscoveryKeyword;
import com.fuma.hiselectors.category.repository.CategoryRepository;
import com.fuma.hiselectors.category.repository.DiscoveryKeywordRepository;
import com.fuma.hiselectors.exception.BusinessException;
import com.fuma.hiselectors.exception.ErrorCode;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CategoryAdminServiceTest {

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private DiscoveryKeywordRepository keywordRepository;

    @InjectMocks
    private CategoryAdminService categoryAdminService;

    private Category beauty;

    @BeforeEach
    void setUp() {
        beauty = Category.builder()
                .code("BEAUTY")
                .name("뷰티")
                .displayOrder(0)
                .build();
    }

    @Test
    @DisplayName("카테고리를 생성한다")
    void createCategory() {
        CategoryCreateRequest request =
                new CategoryCreateRequest("BEAUTY", "뷰티", 0);
        when(categoryRepository.existsByCode("BEAUTY")).thenReturn(false);
        when(categoryRepository.existsByName("뷰티")).thenReturn(false);
        when(categoryRepository.save(any(Category.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        CategoryResponse response = categoryAdminService.create(request);

        assertThat(response.code()).isEqualTo("BEAUTY");
        assertThat(response.name()).isEqualTo("뷰티");
        assertThat(response.displayOrder()).isZero();
        assertThat(response.enabled()).isTrue();
        assertThat(response.keywords()).isEmpty();
        verify(categoryRepository).save(any(Category.class));
    }

    @Test
    @DisplayName("이미 존재하는 카테고리 코드는 등록할 수 없다")
    void createCategoryWithDuplicatedCode() {
        CategoryCreateRequest request =
                new CategoryCreateRequest("BEAUTY", "뷰티", 0);
        when(categoryRepository.existsByCode("BEAUTY")).thenReturn(true);

        assertThatThrownBy(() -> categoryAdminService.create(request))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.CATEGORY_CODE_DUPLICATED);
        verify(categoryRepository, never()).save(any(Category.class));
    }

    @Test
    @DisplayName("이미 존재하는 카테고리 이름은 등록할 수 없다")
    void createCategoryWithDuplicatedName() {
        CategoryCreateRequest request =
                new CategoryCreateRequest("BEAUTY", "뷰티", 0);
        when(categoryRepository.existsByCode("BEAUTY")).thenReturn(false);
        when(categoryRepository.existsByName("뷰티")).thenReturn(true);

        assertThatThrownBy(() -> categoryAdminService.create(request))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.CATEGORY_NAME_DUPLICATED);
        verify(categoryRepository, never()).save(any(Category.class));
    }

    @Test
    @DisplayName("카테고리에 발굴 키워드를 등록한다")
    void addKeyword() {
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(beauty));
        when(keywordRepository.findSameKeywordInOtherCategories("겟레디윗미", 1L))
                .thenReturn(List.of());

        KeywordCreateResponse response = categoryAdminService.addKeyword(
                1L, new KeywordCreateRequest("겟레디윗미", 10));

        assertThat(response.keyword().keyword()).isEqualTo("겟레디윗미");
        assertThat(response.keyword().priority()).isEqualTo(10);
        assertThat(response.keyword().enabled()).isTrue();
        assertThat(response.warnings()).isEmpty();
        assertThat(beauty.getKeywords()).hasSize(1);
        verify(categoryRepository).flush();
    }

    @Test
    @DisplayName("같은 카테고리에는 동일한 키워드를 중복 등록할 수 없다")
    void addDuplicatedKeywordInSameCategory() {
        beauty.addKeyword("겟레디윗미", 10);
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(beauty));

        assertThatThrownBy(() -> categoryAdminService.addKeyword(
                1L, new KeywordCreateRequest("겟레디윗미", 5)))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.KEYWORD_DUPLICATED);
        verify(categoryRepository, never()).flush();
    }

    @Test
    @DisplayName("다른 카테고리의 동일 키워드는 허용하고 경고를 반환한다")
    void addSameKeywordInOtherCategoryWithWarning() {
        Category fashion = Category.builder()
                .code("FASHION")
                .name("패션")
                .build();
        DiscoveryKeyword fashionKeyword = fashion.addKeyword("하울", 5);
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(beauty));
        when(keywordRepository.findSameKeywordInOtherCategories("하울", 1L))
                .thenReturn(List.of(fashionKeyword));

        KeywordCreateResponse response = categoryAdminService.addKeyword(
                1L, new KeywordCreateRequest("하울", 10));

        assertThat(response.keyword().keyword()).isEqualTo("하울");
        assertThat(response.warnings())
                .singleElement()
                .asString()
                .contains("패션");
    }

    @Test
    @DisplayName("존재하지 않는 카테고리를 조회하면 예외가 발생한다")
    void findMissingCategory() {
        when(categoryRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> categoryAdminService.findOne(999L))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.CATEGORY_NOT_FOUND);
    }
}
