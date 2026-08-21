package com.fuma.hiselectors.creator.discovery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fuma.hiselectors.creator.discovery.CreatorRecentActivityBackfillService.BackfillResult;
import com.fuma.hiselectors.creator.repository.CreatorDiscoveryInfoRepository;
import com.fuma.hiselectors.creator.repository.CreatorDiscoveryInfoRepository.RecentActivityBackfillTarget;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CreatorRecentActivityBackfillServiceTest {

    @Mock
    private CreatorDiscoveryInfoRepository discoveryInfoRepository;

    @Mock
    private YoutubeDiscoveryClient youtubeClient;

    @Test
    @DisplayName("NULL인 YouTube 활동 수만 채우고 한 계정 실패 후에도 계속한다")
    void backfillNullCountsAndContinueAfterFailure() {
        RecentActivityBackfillTarget first = target(1L, "UC-first");
        RecentActivityBackfillTarget failed = target(2L, "UC-failed");
        RecentActivityBackfillTarget third = target(3L, "UC-third");
        when(discoveryInfoRepository.findRecentActivityBackfillTargets("YOUTUBE"))
                .thenReturn(List.of(first, failed, third));
        when(youtubeClient.fetchRecent90DayContentCount("UC-first")).thenReturn(0);
        when(youtubeClient.fetchRecent90DayContentCount("UC-failed"))
                .thenThrow(new RuntimeException("YouTube 오류"));
        when(youtubeClient.fetchRecent90DayContentCount("UC-third")).thenReturn(12);
        when(discoveryInfoRepository.fillRecent90DayContentCount(1L, 0)).thenReturn(1);
        when(discoveryInfoRepository.fillRecent90DayContentCount(3L, 12)).thenReturn(1);

        BackfillResult result = service().run();

        assertThat(result).isEqualTo(new BackfillResult(3, 2, 1, 0));
        verify(discoveryInfoRepository).fillRecent90DayContentCount(1L, 0);
        verify(discoveryInfoRepository).fillRecent90DayContentCount(3L, 12);
        verify(discoveryInfoRepository, never()).fillRecent90DayContentCount(2L, 0);
    }

    @Test
    @DisplayName("외부 조회 뒤 다른 작업이 먼저 채운 행은 덮어쓰지 않는다")
    void skipAlreadyUpdatedRow() {
        RecentActivityBackfillTarget target = target(1L, "UC-raced");
        when(discoveryInfoRepository.findRecentActivityBackfillTargets("YOUTUBE"))
                .thenReturn(List.of(target));
        when(youtubeClient.fetchRecent90DayContentCount("UC-raced")).thenReturn(7);
        when(discoveryInfoRepository.fillRecent90DayContentCount(1L, 7)).thenReturn(0);

        assertThat(service().run()).isEqualTo(new BackfillResult(1, 0, 0, 1));
    }

    @Test
    @DisplayName("예상보다 후보가 많으면 API 호출 전에 중단한다")
    void rejectTooManyCandidates() {
        List<RecentActivityBackfillTarget> targets = IntStream.rangeClosed(1, 201)
                .mapToObj(index -> target((long) index, "UC-" + index))
                .toList();
        when(discoveryInfoRepository.findRecentActivityBackfillTargets("YOUTUBE"))
                .thenReturn(targets);

        assertThatThrownBy(() -> service().run())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("최대 200건");
        verify(youtubeClient, never()).fetchRecent90DayContentCount(
                org.mockito.ArgumentMatchers.anyString());
    }

    private CreatorRecentActivityBackfillService service() {
        return new CreatorRecentActivityBackfillService(
                discoveryInfoRepository, youtubeClient);
    }

    private RecentActivityBackfillTarget target(Long id, String accountId) {
        return new RecentActivityBackfillTarget() {
            @Override
            public Long getCreatorId() {
                return id;
            }

            @Override
            public String getAccountId() {
                return accountId;
            }
        };
    }
}
