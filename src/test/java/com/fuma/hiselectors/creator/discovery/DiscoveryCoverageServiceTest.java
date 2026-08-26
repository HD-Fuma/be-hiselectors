package com.fuma.hiselectors.creator.discovery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fuma.hiselectors.category.model.Category;
import com.fuma.hiselectors.category.model.DiscoveryKeyword;
import com.fuma.hiselectors.category.repository.CategoryRepository;
import com.fuma.hiselectors.creator.discovery.dto.DiscoveryCoverageResponse;
import com.fuma.hiselectors.creator.discovery.dto.DiscoveryCoverageResponse.CoverageStatus;
import com.fuma.hiselectors.creator.repository.CreatorDiscoverySourceRepository;
import com.fuma.hiselectors.creator.repository.CreatorDiscoverySourceRepository.DiscoverySourcePair;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DiscoveryCoverageServiceTest {

    private CategoryRepository categoryRepository;
    private CreatorDiscoverySourceRepository sourceRepository;
    private DiscoveryCoverageService service;

    @BeforeEach
    void setUp() {
        categoryRepository = mock(CategoryRepository.class);
        sourceRepository = mock(CreatorDiscoverySourceRepository.class);
        service = new DiscoveryCoverageService(categoryRepository, sourceRepository);
    }

    @Test
    void 다중_키워드의_재등장으로_Chao2_포화도를_계산한다() {
        Category beauty = category(1L, "BEAUTY", "뷰티", 1L, 2L, 3L, 4L);
        when(categoryRepository.findAllByOrderByDisplayOrderAscIdAsc())
                .thenReturn(List.of(beauty));
        when(sourceRepository.findActiveYoutubeSourcePairs()).thenReturn(List.of(
                pair(1L, 1L), pair(1L, 2L), pair(1L, 3L), pair(1L, 4L),
                pair(2L, 2L), pair(2L, 3L), pair(2L, 5L),
                pair(3L, 3L), pair(3L, 4L), pair(3L, 6L),
                pair(4L, 3L), pair(4L, 7L)
        ));

        DiscoveryCoverageResponse result = service.findAll().getFirst();

        assertThat(result.observedCreators()).isEqualTo(7);
        assertThat(result.singletonCreators()).isEqualTo(4);
        assertThat(result.doubletonCreators()).isEqualTo(2);
        assertThat(result.estimatedCreators()).isEqualByComparingTo("8.5");
        assertThat(result.coveragePercent()).isEqualByComparingTo("82.4");
        assertThat(result.status()).isEqualTo(CoverageStatus.MATURING);
        assertThat(result.keywords().getFirst().discoveredCreators()).isEqualTo(4);
        assertThat(result.keywords().getFirst().exclusiveCreators()).isEqualTo(1);
        assertThat(result.keywords().getFirst().overlapPercent())
                .isEqualByComparingTo("75.0");
    }

    @Test
    void 실행_키워드가_세개보다_적으면_추정값을_내지_않는다() {
        Category beauty = category(1L, "BEAUTY", "뷰티", 1L);
        when(categoryRepository.findAllByOrderByDisplayOrderAscIdAsc())
                .thenReturn(List.of(beauty));
        when(sourceRepository.findActiveYoutubeSourcePairs())
                .thenReturn(List.of(pair(1L, 1L)));

        DiscoveryCoverageResponse result = service.findAll().getFirst();

        assertThat(result.status()).isEqualTo(CoverageStatus.INSUFFICIENT_DATA);
        assertThat(result.coveragePercent()).isNull();
        assertThat(result.estimatedCreators()).isNull();
        assertThat(result.recommendation()).contains("현재 1개");
    }

    private Category category(Long id, String code, String name, Long... keywordIds) {
        Category category = mock(Category.class);
        when(category.getId()).thenReturn(id);
        when(category.getCode()).thenReturn(code);
        when(category.getName()).thenReturn(name);
        List<DiscoveryKeyword> keywords = new ArrayList<>();
        for (Long keywordId : keywordIds) {
            DiscoveryKeyword keyword = mock(DiscoveryKeyword.class);
            when(keyword.getId()).thenReturn(keywordId);
            when(keyword.getKeyword()).thenReturn("키워드 " + keywordId);
            when(keyword.getLastRunAt()).thenReturn(LocalDateTime.of(2026, 8, 1, 0, 0));
            keywords.add(keyword);
        }
        when(category.getKeywords()).thenReturn(keywords);
        return category;
    }

    private DiscoverySourcePair pair(Long keywordId, Long creatorId) {
        return new Pair("BEAUTY", keywordId, creatorId);
    }

    private record Pair(String categoryCode, Long keywordId, Long creatorId)
            implements DiscoverySourcePair {

        @Override
        public String getCategoryCode() {
            return categoryCode;
        }

        @Override
        public Long getKeywordId() {
            return keywordId;
        }

        @Override
        public Long getCreatorId() {
            return creatorId;
        }
    }
}
