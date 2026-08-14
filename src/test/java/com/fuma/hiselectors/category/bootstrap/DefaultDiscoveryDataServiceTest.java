package com.fuma.hiselectors.category.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fuma.hiselectors.category.bootstrap.DefaultDiscoveryCategoryWriter.CategoryInitializationResult;
import com.fuma.hiselectors.category.bootstrap.DefaultDiscoveryDataService.InitializationResult;
import com.fuma.hiselectors.category.repository.CategoryRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith(MockitoExtension.class)
class DefaultDiscoveryDataServiceTest {

    @Mock
    private DefaultDiscoveryCategoryWriter categoryWriter;

    @Mock
    private CategoryRepository categoryRepository;

    @InjectMocks
    private DefaultDiscoveryDataService service;

    @Test
    @DisplayName("다른 인스턴스가 먼저 초기화한 카테고리는 건너뛰고 계속 진행한다")
    void continueAfterConcurrentInitialization() {
        when(categoryWriter.initialize(any())).thenAnswer(invocation -> {
            DefaultDiscoveryCatalog.DefaultCategory category = invocation.getArgument(0);
            if (category.code().equals("BEAUTY")) {
                throw new DataIntegrityViolationException("duplicate category");
            }
            return new CategoryInitializationResult(1, category.keywords().size(), 0);
        });
        when(categoryRepository.existsByCode("BEAUTY")).thenReturn(true);

        InitializationResult result = service.initialize();

        assertThat(result.createdCategories()).isEqualTo(8);
        assertThat(result.createdKeywords()).isEqualTo(52);
        assertThat(result.skippedCategories()).isEqualTo(1);
        verify(categoryWriter, times(9)).initialize(any());
    }

    @Test
    @DisplayName("동시 초기화가 아닌 데이터 무결성 오류는 숨기지 않는다")
    void rethrowUnknownIntegrityViolation() {
        DataIntegrityViolationException failure =
                new DataIntegrityViolationException("invalid data");
        when(categoryWriter.initialize(any())).thenThrow(failure);

        assertThatThrownBy(service::initialize).isSameAs(failure);
    }
}
