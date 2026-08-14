package com.fuma.hiselectors.creator.discovery.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fuma.hiselectors.category.model.DiscoveryKeyword;
import com.fuma.hiselectors.category.repository.DiscoveryKeywordRepository;
import com.fuma.hiselectors.creator.discovery.DiscoveryPipelineService;
import com.fuma.hiselectors.creator.discovery.YoutubeDiscoveryProperties;
import com.fuma.hiselectors.creator.discovery.dto.DiscoveryRunResult;
import com.fuma.hiselectors.exception.BusinessException;
import com.fuma.hiselectors.exception.ErrorCode;
import java.util.List;
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

        YoutubeDiscoveryBatchService service = service(306, 25, 10);
        YoutubeDiscoveryBatchResult batchResult = service.run();

        InOrder order = inOrder(discoveryPipelineService);
        order.verify(discoveryPipelineService).runByKeyword(1L, 25);
        order.verify(discoveryPipelineService).runByKeyword(2L, 25);
        order.verify(discoveryPipelineService).runByKeyword(3L, 25);
        verify(discoveryPipelineService, never()).runByKeyword(4L, 25);

        assertThat(batchResult.runnableKeywords()).isEqualTo(4);
        assertThat(batchResult.attemptedKeywords()).isEqualTo(3);
        assertThat(batchResult.succeededKeywords()).isEqualTo(3);
        assertThat(batchResult.failedKeywords()).isZero();
        assertThat(batchResult.reservedQuota()).isEqualTo(306);
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

        YoutubeDiscoveryBatchResult batchResult = service(204, 25, 10).run();

        verify(discoveryPipelineService).runByKeyword(1L, 25);
        verify(discoveryPipelineService).runByKeyword(2L, 25);
        assertThat(batchResult.attemptedKeywords()).isEqualTo(2);
        assertThat(batchResult.succeededKeywords()).isEqualTo(1);
        assertThat(batchResult.failedKeywords()).isEqualTo(1);
        assertThat(batchResult.reservedQuota()).isEqualTo(204);
        assertThat(batchResult.consumedQuota()).isEqualTo(102);
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
    @DisplayName("API 키가 없으면 설정 오류를 반환한다")
    void failWithoutApiKey() {
        YoutubeDiscoveryProperties withoutApiKey =
                new YoutubeDiscoveryProperties(null, 10_000, 25);
        YoutubeDiscoveryBatchProperties batchProperties =
                new YoutubeDiscoveryBatchProperties(10);
        YoutubeDiscoveryBatchService service = new YoutubeDiscoveryBatchService(
                keywordRepository, discoveryPipelineService, withoutApiKey, batchProperties);

        assertThatThrownBy(service::run)
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.YOUTUBE_API_KEY_MISSING);
        verify(keywordRepository, never()).findRunnable();
    }

    private YoutubeDiscoveryBatchService service(
            int dailyQuota, int maxResults, int maxKeywords) {
        YoutubeDiscoveryProperties discovery =
                new YoutubeDiscoveryProperties("test-key", dailyQuota, maxResults);
        YoutubeDiscoveryBatchProperties batchProperties =
                new YoutubeDiscoveryBatchProperties(maxKeywords);
        return new YoutubeDiscoveryBatchService(
                keywordRepository, discoveryPipelineService, discovery, batchProperties);
    }

    private DiscoveryKeyword keyword(Long id, String value) {
        DiscoveryKeyword keyword = mock(DiscoveryKeyword.class);
        lenient().when(keyword.getId()).thenReturn(id);
        lenient().when(keyword.getKeyword()).thenReturn(value);
        return keyword;
    }

    private DiscoveryRunResult result(
            String keyword, int discovered, int created, int updated, int quota) {
        return new DiscoveryRunResult(
                keyword, "BEAUTY", discovered, created, updated, quota);
    }
}
