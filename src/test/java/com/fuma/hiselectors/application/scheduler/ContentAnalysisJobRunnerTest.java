package com.fuma.hiselectors.application.scheduler;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.fuma.hiselectors.application.repository.ApplicationRepository;
import com.fuma.hiselectors.application.service.ApplicationAnalysisService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.ApplicationArguments;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.test.util.ReflectionTestUtils;

class ContentAnalysisJobRunnerTest {

    @Test
    void runsOneAnalysisAndClosesContext() {
        ContentAnalysisScheduler scheduler = mock(ContentAnalysisScheduler.class);
        ConfigurableApplicationContext context = mock(ConfigurableApplicationContext.class);
        ApplicationArguments arguments = mock(ApplicationArguments.class);

        new ContentAnalysisJobRunner(scheduler, context).run(arguments);

        verify(scheduler).analyzeOne();
        verify(context).close();
    }

    @Test
    void disabledSchedulerDoesNotQueryDatabase() {
        ApplicationRepository repository = mock(ApplicationRepository.class);
        ApplicationAnalysisService service = mock(ApplicationAnalysisService.class);
        ContentAnalysisScheduler scheduler = new ContentAnalysisScheduler(repository, service);
        ReflectionTestUtils.setField(scheduler, "schedulerEnabled", false);

        scheduler.analyzeCollectedApplications();

        verifyNoInteractions(repository, service);
    }
}
