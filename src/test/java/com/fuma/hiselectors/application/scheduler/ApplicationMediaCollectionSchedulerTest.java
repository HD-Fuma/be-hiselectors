package com.fuma.hiselectors.application.scheduler;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fuma.hiselectors.application.model.Application;
import com.fuma.hiselectors.application.repository.ApplicationRepository;
import com.fuma.hiselectors.application.service.ApplicationMediaService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

class ApplicationMediaCollectionSchedulerTest {

    @Test
    void collectsEveryTargetEvenWhenOneFails() {
        ApplicationRepository repository = mock(ApplicationRepository.class);
        ApplicationMediaService mediaService = mock(ApplicationMediaService.class);
        Application first = application(1L);
        Application second = application(2L);
        when(repository
                .findByMediaCollectionStatusInAndMediaCollectionRetryCountLessThanOrderByIdAsc(
                        any(), eq(3), any(Pageable.class)))
                .thenReturn(List.of(first, second));
        doThrow(new IllegalStateException("API failed")).when(mediaService).collect(1L);

        ApplicationMediaCollectionScheduler scheduler =
                new ApplicationMediaCollectionScheduler(repository, mediaService);
        ReflectionTestUtils.setField(scheduler, "batchSize", 20);

        scheduler.collectPendingApplications();

        verify(mediaService).collect(1L);
        verify(mediaService).collect(2L);
    }

    private Application application(Long id) {
        Application application = Application.builder().build();
        ReflectionTestUtils.setField(application, "id", id);
        return application;
    }
}
