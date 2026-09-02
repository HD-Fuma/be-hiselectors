package com.fuma.hiselectors.taskrun.queue;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.fuma.hiselectors.taskrun.repository.TaskRunRepository;
import com.fuma.hiselectors.taskrun.service.TaskRunProgressStream;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TaskQueueProgressRelayContractTest {
    @Test void noSubscribersMeansNoDatabasePolling() {
        var repository = mock(TaskRunRepository.class);
        var stream = mock(TaskRunProgressStream.class);
        var relay = new TaskQueueProgressRelay(repository, stream, properties(), Clock.systemUTC());
        try {
            relay.relay();
            verifyNoInteractions(repository);
        } finally { relay.stop(); }
    }

    @Test void unchangedVersionsDoNotRepeatedlyRefreshTheAdminPanel() {
        var repository = mock(TaskRunRepository.class);
        var stream = mock(TaskRunProgressStream.class);
        var change = mock(TaskRunRepository.QueueChange.class);
        UUID id = UUID.randomUUID();
        when(change.getRunId()).thenReturn(id);
        when(change.getVersion()).thenReturn(1L);
        when(stream.hasSubscribers()).thenReturn(true);
        when(repository.findQueueChangesSince(any(), any())).thenReturn(List.of(change));
        var relay = new TaskQueueProgressRelay(repository, stream, properties(), Clock.systemUTC());
        try {
            relay.relay();
            relay.relay();
            verify(stream).publishChanged(id);
            when(change.getVersion()).thenReturn(2L);
            relay.relay();
            verify(stream, times(2)).publishChanged(id);
        } finally { relay.stop(); }
    }

    private TaskQueueProperties properties() {
        return new TaskQueueProperties(true, false, "https://sqs.test/queue", "", "ap-northeast-2",
                1, 300, 120, 30, 3, 30, 600);
    }
}
