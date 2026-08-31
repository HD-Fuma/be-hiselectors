package com.fuma.hiselectors.selectors.scheduler;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fuma.hiselectors.selectors.dto.SelectorSnsEnrichmentResponse;
import com.fuma.hiselectors.selectors.service.SelectorSnsEnrichmentService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class SelectorSnsEnrichmentSchedulerTest {

    @Test
    void doesNothingWhenDisabled() {
        SelectorSnsEnrichmentService service = mock(SelectorSnsEnrichmentService.class);
        SelectorSnsEnrichmentScheduler scheduler = new SelectorSnsEnrichmentScheduler(service);
        ReflectionTestUtils.setField(scheduler, "schedulerEnabled", false);
        ReflectionTestUtils.setField(scheduler, "batchSize", 5);

        scheduler.enrichMissingSelectors();

        verify(service, never()).enrichMissing(false, 5);
    }

    @Test
    void enrichesMissingWhenEnabled() {
        SelectorSnsEnrichmentService service = mock(SelectorSnsEnrichmentService.class);
        when(service.enrichMissing(false, 5)).thenReturn(
                new SelectorSnsEnrichmentResponse.Batch(2, 1, 1, 0, List.of()));
        SelectorSnsEnrichmentScheduler scheduler = new SelectorSnsEnrichmentScheduler(service);
        ReflectionTestUtils.setField(scheduler, "schedulerEnabled", true);
        ReflectionTestUtils.setField(scheduler, "batchSize", 5);

        scheduler.enrichMissingSelectors();

        verify(service).enrichMissing(false, 5);
    }
}
