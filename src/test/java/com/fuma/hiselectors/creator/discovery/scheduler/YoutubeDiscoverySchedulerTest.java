package com.fuma.hiselectors.creator.discovery.scheduler;

import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class YoutubeDiscoverySchedulerTest {

    @Mock
    private YoutubeDiscoveryBatchService batchService;

    @InjectMocks
    private YoutubeDiscoveryScheduler scheduler;

    @Test
    @DisplayName("스케줄 실행 시 일일 발굴 서비스를 호출한다")
    void delegateDailyRun() {
        scheduler.runDaily();

        verify(batchService).runDaily();
    }
}
