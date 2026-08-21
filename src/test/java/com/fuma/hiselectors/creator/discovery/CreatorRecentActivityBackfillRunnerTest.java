package com.fuma.hiselectors.creator.discovery;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fuma.hiselectors.creator.discovery.CreatorRecentActivityBackfillService.BackfillResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.context.ConfigurableApplicationContext;

class CreatorRecentActivityBackfillRunnerTest {

    @Test
    @DisplayName("모든 백필이 성공하면 일회성 애플리케이션을 종료한다")
    void closeContextAfterSuccess() {
        CreatorRecentActivityBackfillService service =
                mock(CreatorRecentActivityBackfillService.class);
        ConfigurableApplicationContext context = mock(ConfigurableApplicationContext.class);
        when(service.run()).thenReturn(new BackfillResult(120, 120, 0, 0));

        new CreatorRecentActivityBackfillRunner(service, context)
                .run(new DefaultApplicationArguments());

        verify(context).close();
    }

    @Test
    @DisplayName("일부 백필이 실패하면 프로세스를 실패시켜 재실행할 수 있게 한다")
    void failProcessWhenAnyTargetFailed() {
        CreatorRecentActivityBackfillService service =
                mock(CreatorRecentActivityBackfillService.class);
        ConfigurableApplicationContext context = mock(ConfigurableApplicationContext.class);
        BackfillResult result = new BackfillResult(120, 119, 1, 0);
        when(service.run()).thenReturn(result);

        assertThatThrownBy(() -> new CreatorRecentActivityBackfillRunner(service, context)
                .run(new DefaultApplicationArguments()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(result.toString());
    }
}
