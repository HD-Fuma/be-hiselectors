package com.fuma.hiselectors.selectors.excellence.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fuma.hiselectors.selectors.excellence.service.SelectorExcellenceSelectionService;
import com.fuma.hiselectors.selectors.excellence.service.SelectorExcellenceSelectionService.BatchResult;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.Scheduled;

class SelectorExcellenceSchedulerTest {

    @Test
    void delegatesSelectionBatch() {
        SelectorExcellenceSelectionService service = mock(SelectorExcellenceSelectionService.class);
        when(service.selectEligibleGenerations()).thenReturn(new BatchResult(2, 2, 0, 7, 0));
        SelectorExcellenceScheduler scheduler = new SelectorExcellenceScheduler(service);

        scheduler.selectExcellentSelectors();

        verify(service).selectEligibleGenerations();
    }

    @Test
    void runsEveryDayAtTwentyPastMidnightInSeoul() throws NoSuchMethodException {
        Method method = SelectorExcellenceScheduler.class
                .getDeclaredMethod("selectExcellentSelectors");

        Scheduled scheduled = method.getAnnotation(Scheduled.class);

        assertThat(scheduled).isNotNull();
        assertThat(scheduled.cron())
                .isEqualTo("${selectors.excellence.cron:0 20 0 * * *}");
        assertThat(scheduled.zone())
                .isEqualTo("${selectors.excellence.zone:Asia/Seoul}");
    }
}
