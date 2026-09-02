package com.fuma.hiselectors.creator.discovery.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fuma.hiselectors.category.model.Category;
import com.fuma.hiselectors.category.model.DiscoveryKeyword;
import com.fuma.hiselectors.category.repository.DiscoveryKeywordRepository;
import com.fuma.hiselectors.creator.discovery.DiscoveryPipelineService;
import com.fuma.hiselectors.creator.discovery.YoutubeDiscoveryProperties;
import com.fuma.hiselectors.creator.discovery.batch.InstagramDiscoveryBatchService;
import com.fuma.hiselectors.creator.discovery.dto.DiscoveryRunResult;
import com.fuma.hiselectors.exception.BusinessException;
import com.fuma.hiselectors.exception.ErrorCode;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class YoutubeDiscoveryBatchServiceTest {

    @Mock
    private DiscoveryKeywordRepository keywordRepository;

    @Mock
    private DiscoveryPipelineService discoveryPipelineService;

    @Mock
    private InstagramDiscoveryBatchService instagramDiscoveryBatchService;

    @Test
    @DisplayName("Repository가 준 순서대로 실행하고 쿼터 예산을 넘기지 않는다")
    void runInRepositoryOrderWithinQuota() {
        DiscoveryKeyword first = keyword(1L, "첫 번째");
        DiscoveryKeyword second = keyword(2L, "두 번째");
        DiscoveryKeyword third = keyword(3L, "세 번째");
        DiscoveryKeyword overBudget = keyword(4L, "쿼터 밖");
        when(keywordRepository.findRunnable())
                .thenReturn(List.of(first, second, third, overBudget));
        when(discoveryPipelineService.runByKeyword(1L, 25))
                .thenReturn(result("첫 번째", 2, 1, 1, 102));
        when(discoveryPipelineService.runByKeyword(2L, 25))
                .thenReturn(result("두 번째", 1, 1, 0, 100));
        when(discoveryPipelineService.runByKeyword(3L, 25))
                .thenReturn(result("세 번째", 0, 0, 0, 100));

        YoutubeDiscoveryBatchService service = service(381, 25, 10);
        YoutubeDiscoveryBatchResult batchResult = service.run();

        InOrder order = inOrder(discoveryPipelineService, instagramDiscoveryBatchService);
        order.verify(discoveryPipelineService).runByKeyword(1L, 25);
        order.verify(discoveryPipelineService).runByKeyword(2L, 25);
        order.verify(discoveryPipelineService).runByKeyword(3L, 25);
        order.verify(instagramDiscoveryBatchService).run();
        verify(discoveryPipelineService, never()).runByKeyword(4L, 25);

        assertThat(batchResult.runnableKeywords()).isEqualTo(4);
        assertThat(batchResult.attemptedKeywords()).isEqualTo(3);
        assertThat(batchResult.succeededKeywords()).isEqualTo(3);
        assertThat(batchResult.failedKeywords()).isZero();
        assertThat(batchResult.reservedQuota()).isEqualTo(381);
        assertThat(batchResult.consumedQuota()).isEqualTo(302);
        assertThat(batchResult.discovered()).isEqualTo(3);
        assertThat(batchResult.created()).isEqualTo(2);
        assertThat(batchResult.updated()).isEqualTo(1);
    }

    @Test
    @DisplayName("한 키워드가 실패해도 다음 키워드를 계속 실행한다")
    void continueAfterKeywordFailure() {
        DiscoveryKeyword failed = keyword(1L, "실패");
        DiscoveryKeyword succeeded = keyword(2L, "성공");
        when(keywordRepository.findRunnable()).thenReturn(List.of(failed, succeeded));
        when(discoveryPipelineService.runByKeyword(1L, 25))
                .thenThrow(new RuntimeException("외부 API 실패"));
        when(discoveryPipelineService.runByKeyword(2L, 25))
                .thenReturn(result("성공", 3, 2, 1, 102));

        YoutubeDiscoveryBatchResult batchResult = service(254, 25, 10).run();

        verify(discoveryPipelineService).runByKeyword(1L, 25);
        verify(discoveryPipelineService).runByKeyword(2L, 25);
        assertThat(batchResult.attemptedKeywords()).isEqualTo(2);
        assertThat(batchResult.succeededKeywords()).isEqualTo(1);
        assertThat(batchResult.failedKeywords()).isEqualTo(1);
        assertThat(batchResult.reservedQuota()).isEqualTo(254);
        assertThat(batchResult.consumedQuota()).isEqualTo(102);
    }

    @Test
    @DisplayName("키워드 시도마다 중복 제거된 크리에이터 수를 전달한다")
    void reportUniqueCreatorsAfterEveryKeywordAttempt() {
        DiscoveryKeyword first = keyword(1L, "첫 번째");
        DiscoveryKeyword failed = keyword(2L, "실패");
        DiscoveryKeyword third = keyword(3L, "세 번째");
        when(keywordRepository.findRunnable()).thenReturn(List.of(first, failed, third));
        when(discoveryPipelineService.runByKeyword(1L, 25))
                .thenReturn(result("첫 번째", 2, 2, 0, 102, Set.of(10L, 20L)));
        when(discoveryPipelineService.runByKeyword(2L, 25))
                .thenThrow(new RuntimeException("외부 API 실패"));
        when(discoveryPipelineService.runByKeyword(3L, 25))
                .thenReturn(result("세 번째", 2, 1, 1, 102, Set.of(20L, 30L)));
        List<YoutubeDiscoveryBatchResult> snapshots = new ArrayList<>();

        YoutubeDiscoveryBatchResult result = service(381, 25, 10)
                .runYoutubeOnly(snapshots::add);

        assertThat(result.targetKeywords()).isEqualTo(3);
        assertThat(result.uniqueCollectedCreators()).isEqualTo(3);
        assertThat(snapshots).hasSize(3);
        assertThat(snapshots)
                .extracting(YoutubeDiscoveryBatchResult::attemptedKeywords)
                .containsExactly(1, 2, 3);
        assertThat(snapshots)
                .extracting(YoutubeDiscoveryBatchResult::uniqueCollectedCreators)
                .containsExactly(2, 2, 3);
    }

    @Test
    @DisplayName("실행당 최대 키워드 개수를 적용한다")
    void applyMaxKeywordsPerRun() {
        DiscoveryKeyword first = keyword(1L, "첫 번째");
        DiscoveryKeyword limited = keyword(2L, "제한 밖");
        when(keywordRepository.findRunnable()).thenReturn(List.of(first, limited));
        when(discoveryPipelineService.runByKeyword(1L, 10))
                .thenReturn(result("첫 번째", 1, 1, 0, 102));

        YoutubeDiscoveryBatchResult batchResult = service(10_000, 10, 1).run();

        verify(discoveryPipelineService).runByKeyword(1L, 10);
        verify(discoveryPipelineService, never()).runByKeyword(2L, 10);
        assertThat(batchResult.attemptedKeywords()).isEqualTo(1);
    }

    @Test
    @DisplayName("테스트 모드는 카테고리마다 첫 키워드 하나만 실행한다")
    void runOneKeywordPerCategoryInTestMode() {
        DiscoveryKeyword beautyFirst = keyword(1L, "뷰티 첫 번째", 10L);
        DiscoveryKeyword beautySecond = keyword(2L, "뷰티 두 번째", 10L);
        DiscoveryKeyword fashionFirst = keyword(3L, "패션 첫 번째", 20L);
        when(keywordRepository.findRunnable())
                .thenReturn(List.of(beautyFirst, beautySecond, fashionFirst));
        when(discoveryPipelineService.runByKeyword(1L, 25))
                .thenReturn(result("뷰티 첫 번째", 1, 1, 0, 102));
        when(discoveryPipelineService.runByKeyword(3L, 25))
                .thenReturn(result("패션 첫 번째", 1, 1, 0, 102));

        YoutubeDiscoveryBatchResult result = service(10_000, 25, 50)
                .runYoutubeOnly(true, ignored -> { });

        verify(discoveryPipelineService).runByKeyword(1L, 25);
        verify(discoveryPipelineService, never()).runByKeyword(2L, 25);
        verify(discoveryPipelineService).runByKeyword(3L, 25);
        assertThat(result.runnableKeywords()).isEqualTo(3);
        assertThat(result.targetKeywords()).isEqualTo(2);
        assertThat(result.attemptedKeywords()).isEqualTo(2);
    }

    @Test
    @DisplayName("선택 카테고리의 활성 키워드만 실행한다")
    void runOnlySelectedCategory() {
        DiscoveryKeyword first = keyword(1L, "뷰티 첫 번째");
        DiscoveryKeyword second = keyword(2L, "뷰티 두 번째");
        when(keywordRepository.findRunnableByCategoryId(10L))
                .thenReturn(List.of(first, second));
        when(discoveryPipelineService.runByKeyword(1L, 25))
                .thenReturn(result("뷰티 첫 번째", 1, 1, 0, 102));
        when(discoveryPipelineService.runByKeyword(2L, 25))
                .thenReturn(result("뷰티 두 번째", 1, 0, 1, 102));

        YoutubeDiscoveryBatchResult result = service(10_000, 25, 50)
                .runYoutubeOnlyByCategory(10L, ignored -> { });

        verify(keywordRepository, never()).findRunnable();
        assertThat(result.runnableKeywords()).isEqualTo(2);
        assertThat(result.attemptedKeywords()).isEqualTo(2);
    }

    @Test
    @DisplayName("이번 달 필터를 키워드 파이프라인에 전달한다")
    void passCurrentMonthFilter() {
        DiscoveryKeyword keyword = keyword(1L, "이번 달");
        when(keywordRepository.findRunnable()).thenReturn(List.of(keyword));
        when(discoveryPipelineService.runByKeyword(1L, 25, true))
                .thenReturn(result("이번 달", 1, 1, 0, 102));

        service(10_000, 25, 50).runYoutubeOnly(false, true, ignored -> { });

        verify(discoveryPipelineService).runByKeyword(1L, 25, true);
    }

    @Test
    @DisplayName("API 키가 없으면 설정 오류를 반환한다")
    void failWithoutApiKey() {
        YoutubeDiscoveryProperties withoutApiKey =
                new YoutubeDiscoveryProperties(null, 10_000, 25);
        YoutubeDiscoveryBatchProperties batchProperties =
                new YoutubeDiscoveryBatchProperties(10);
        YoutubeDiscoveryBatchService service = new YoutubeDiscoveryBatchService(
                keywordRepository, discoveryPipelineService, withoutApiKey, batchProperties,
                instagramDiscoveryBatchService);

        assertThatThrownBy(service::run)
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.YOUTUBE_API_KEY_MISSING);
        verify(keywordRepository, never()).findRunnable();
        verify(instagramDiscoveryBatchService, never()).run();
    }

    @Test
    @DisplayName("진행 콜백이 null이면 조회 전에 실패한다")
    void rejectNullProgressCallbackBeforeQuery() {
        YoutubeDiscoveryBatchService service = new YoutubeDiscoveryBatchService(
                keywordRepository,
                discoveryPipelineService,
                new YoutubeDiscoveryProperties(null, 10_000, 25),
                new YoutubeDiscoveryBatchProperties(10),
                instagramDiscoveryBatchService);

        assertThatThrownBy(() -> service.runYoutubeOnly(null))
                .isInstanceOf(NullPointerException.class);

        verifyNoInteractions(keywordRepository, discoveryPipelineService);
    }

    @Test
    @DisplayName("키워드당 검색 개수는 YouTube API 허용 범위여야 한다")
    void rejectInvalidMaxResults() {
        assertThatThrownBy(() -> new YoutubeDiscoveryProperties("test-key", 10_000, 0)
                .maxResultsOrDefault())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("between 1 and 50");
        assertThatThrownBy(() -> new YoutubeDiscoveryProperties("test-key", 10_000, 51)
                .maxResultsOrDefault())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("between 1 and 50");
    }

    private YoutubeDiscoveryBatchService service(
            int dailyQuota, int maxResults, int maxKeywords) {
        YoutubeDiscoveryProperties discovery =
                new YoutubeDiscoveryProperties("test-key", dailyQuota, maxResults);
        YoutubeDiscoveryBatchProperties batchProperties =
                new YoutubeDiscoveryBatchProperties(maxKeywords);
        return new YoutubeDiscoveryBatchService(
                keywordRepository, discoveryPipelineService, discovery, batchProperties,
                instagramDiscoveryBatchService);
    }

    private DiscoveryKeyword keyword(Long id, String value) {
        DiscoveryKeyword keyword = mock(DiscoveryKeyword.class);
        lenient().when(keyword.getId()).thenReturn(id);
        lenient().when(keyword.getKeyword()).thenReturn(value);
        return keyword;
    }

    private DiscoveryKeyword keyword(Long id, String value, Long categoryId) {
        DiscoveryKeyword keyword = keyword(id, value);
        Category category = mock(Category.class);
        when(category.getId()).thenReturn(categoryId);
        when(keyword.getCategory()).thenReturn(category);
        return keyword;
    }

    private DiscoveryRunResult result(
            String keyword, int discovered, int created, int updated, int quota) {
        return new DiscoveryRunResult(
                keyword, "BEAUTY", discovered, created, updated, quota);
    }

    private DiscoveryRunResult result(
            String keyword, int discovered, int created, int updated, int quota,
            Set<Long> creatorIds) {
        return new DiscoveryRunResult(
                keyword, "BEAUTY", discovered, created, updated, quota, creatorIds);
    }
}
