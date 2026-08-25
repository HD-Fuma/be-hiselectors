package com.fuma.hiselectors.taskrun.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import org.mockito.ArgumentCaptor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

class TaskRunProgressStreamTest {

    private static final Duration EMITTER_TIMEOUT = Duration.ofMinutes(5);
    private static final Duration HEARTBEAT_INTERVAL = Duration.ofDays(1);
    private static final UUID RUN_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    private final List<TaskRunProgressStream> streams = new ArrayList<>();

    @AfterEach
    void tearDown() {
        streams.forEach(TaskRunProgressStream::close);
    }

    @Test
    void subscribeReturnsFiniteEmitterAndImmediatelyQueuesConnectedComment() {
        TestEmitter emitter = new TestEmitter(EMITTER_TIMEOUT.toMillis());
        TaskRunProgressStream stream = stream(8, emitter);

        SseEmitter subscribed = stream.subscribe();

        assertThat(subscribed).isSameAs(emitter);
        assertThat(subscribed.getTimeout()).isEqualTo(EMITTER_TIMEOUT.toMillis());
        emitter.awaitFrameCount(1);
        assertThat(emitter.comments()).containsExactly("connected");
        assertThat(emitter.drainThread.get()).startsWith("task-run-progress-");
        assertThat(emitter.virtualThread.get()).isTrue();
    }

    @Test
    void publishesInOrderToMultipleIndependentSubscribers() {
        TestEmitter first = new TestEmitter(EMITTER_TIMEOUT.toMillis());
        TestEmitter second = new TestEmitter(EMITTER_TIMEOUT.toMillis());
        TaskRunProgressStream stream = stream(8, first, second);
        stream.subscribe();
        stream.subscribe();
        first.awaitFrameCount(1);
        second.awaitFrameCount(1);
        TaskRunProgressEvent one = event(1L);
        TaskRunProgressEvent two = event(2L);
        TaskRunProgressEvent three = event(3L);

        stream.publish(one);
        stream.publish(two);
        stream.publish(three);

        first.awaitEventCount(3);
        second.awaitEventCount(3);
        assertThat(first.events()).containsExactly(one, two, three);
        assertThat(second.events()).containsExactly(one, two, three);
    }

    @Test
    void slowSubscriberDoesNotDelayPublisherOrOtherSubscriber() throws Exception {
        TestEmitter slow = new TestEmitter(EMITTER_TIMEOUT.toMillis());
        slow.blockEvents = true;
        TestEmitter fast = new TestEmitter(EMITTER_TIMEOUT.toMillis());
        TaskRunProgressStream stream = stream(8, slow, fast);
        stream.subscribe();
        stream.subscribe();
        slow.awaitFrameCount(1);
        fast.awaitFrameCount(1);

        stream.publish(event(1L));
        assertThat(slow.eventSendStarted.await(1, TimeUnit.SECONDS)).isTrue();
        CountDownLatch published = new CountDownLatch(1);
        Thread.startVirtualThread(() -> {
            stream.publish(event(2L));
            published.countDown();
        });

        assertThat(published.await(1, TimeUnit.SECONDS)).isTrue();
        fast.awaitEventCount(2);
        assertThat(fast.events()).extracting(TaskRunProgressEvent::processedCount)
                .containsExactly(1L, 2L);
        slow.releaseEvents.countDown();
    }

    @Test
    void burstCoalescesAbsoluteSnapshotsAndEventuallyDeliversLatest() throws Exception {
        TestEmitter emitter = new TestEmitter(EMITTER_TIMEOUT.toMillis());
        emitter.blockEvents = true;
        emitter.ignoreSendInterrupts = true;
        emitter.expectProcessedCount(1_000L);
        TaskRunProgressStream stream = stream(256, emitter);
        stream.subscribe();
        emitter.awaitFrameCount(1);
        stream.publish(event(1L, 1_000L));
        assertThat(emitter.eventSendStarted.await(1, TimeUnit.SECONDS)).isTrue();
        CountDownLatch published = new CountDownLatch(1);
        Thread.startVirtualThread(() -> {
            for (long processed = 2; processed <= 1_000; processed++) {
                stream.publish(event(processed, 1_000L));
            }
            published.countDown();
        });

        assertThat(published.await(1, TimeUnit.SECONDS)).isTrue();
        boolean connectedAfterBurst = stream.subscriberCount() == 1;
        emitter.releaseEvents.countDown();

        assertThat(connectedAfterBurst).isTrue();
        assertThat(emitter.awaitExpectedProcessedCount()).isTrue();
        List<Long> delivered = emitter.events().stream()
                .map(TaskRunProgressEvent::processedCount)
                .toList();
        assertThat(delivered).isSorted();
        assertThat(delivered.getLast()).isEqualTo(1_000L);
        assertThat(emitter.completionAttempted.getCount()).isEqualTo(1L);
    }

    @Test
    void permanentlyStalledSendCoalescesWithoutCompletionWork() throws Exception {
        TestEmitter stalled = new TestEmitter(EMITTER_TIMEOUT.toMillis());
        stalled.blockEvents = true;
        stalled.ignoreSendInterrupts = true;
        TaskRunProgressStream stream = stream(1, stalled);
        stream.subscribe();
        stalled.awaitFrameCount(1);

        stream.publish(event(1L));
        assertThat(stalled.eventSendStarted.await(1, TimeUnit.SECONDS)).isTrue();
        stream.publish(event(2L));
        CountDownLatch published = new CountDownLatch(1);
        Thread.startVirtualThread(() -> {
            stream.publish(event(3L));
            published.countDown();
        });

        assertThat(published.await(1, TimeUnit.SECONDS)).isTrue();
        assertThat(stalled.interrupted.await(300, TimeUnit.MILLISECONDS)).isFalse();
        assertThat(stalled.completionAttempted.await(300, TimeUnit.MILLISECONDS)).isFalse();
        assertThat(stream.subscriberCount()).isEqualTo(1);
    }

    @Test
    void fullQueueCoalescesSlowSubscriberWhileHealthySubscriberReceives() throws Exception {
        TestEmitter slow = new TestEmitter(EMITTER_TIMEOUT.toMillis());
        slow.blockEvents = true;
        slow.ignoreSendInterrupts = true;
        slow.expectProcessedCount(3L);
        TestEmitter fast = new TestEmitter(EMITTER_TIMEOUT.toMillis());
        TaskRunProgressStream stream = stream(1, slow, fast);
        stream.subscribe();
        stream.subscribe();
        slow.awaitFrameCount(1);
        fast.awaitFrameCount(1);

        stream.publish(event(1L));
        assertThat(slow.eventSendStarted.await(1, TimeUnit.SECONDS)).isTrue();
        fast.awaitEventCount(1);
        stream.publish(event(2L));
        fast.awaitEventCount(2);
        CountDownLatch published = new CountDownLatch(1);
        Thread.startVirtualThread(() -> {
            stream.publish(event(3L));
            published.countDown();
        });

        assertThat(published.await(1, TimeUnit.SECONDS)).isTrue();
        assertThat(slow.interrupted.await(300, TimeUnit.MILLISECONDS)).isFalse();
        fast.awaitEventCount(3);
        assertThat(fast.events()).extracting(TaskRunProgressEvent::processedCount)
                .containsExactly(1L, 2L, 3L);
        slow.releaseEvents.countDown();
        assertThat(slow.awaitExpectedProcessedCount()).isTrue();
        assertThat(slow.events()).extracting(TaskRunProgressEvent::processedCount)
                .containsExactly(1L, 3L);
        assertThat(stream.subscriberCount()).isEqualTo(2);
        assertThat(slow.completionAttempted.getCount()).isEqualTo(1L);
    }

    @Test
    void fullQueueDropsHeartbeatAndRetainsProgress() throws Exception {
        TestEmitter emitter = new TestEmitter(EMITTER_TIMEOUT.toMillis());
        emitter.blockEvents = true;
        emitter.ignoreSendInterrupts = true;
        emitter.expectProcessedCount(2L);
        TaskRunProgressStream stream = stream(1, emitter);
        stream.subscribe();
        emitter.awaitFrameCount(1);

        stream.publish(event(1L));
        assertThat(emitter.eventSendStarted.await(1, TimeUnit.SECONDS)).isTrue();
        stream.publish(event(2L));
        stream.heartbeat();

        emitter.releaseEvents.countDown();
        assertThat(emitter.awaitExpectedProcessedCount()).isTrue();
        assertThat(emitter.events()).extracting(TaskRunProgressEvent::processedCount)
                .containsExactly(1L, 2L);
        assertThat(emitter.comments()).containsExactly("connected");
        assertThat(stream.subscriberCount()).isEqualTo(1);
    }

    @Test
    void completionTimeoutAndClientErrorEachCleanUpTheirOwnSubscriber() {
        TestEmitter completed = new TestEmitter(EMITTER_TIMEOUT.toMillis());
        TestEmitter timedOut = new TestEmitter(EMITTER_TIMEOUT.toMillis());
        TestEmitter errored = new TestEmitter(EMITTER_TIMEOUT.toMillis());
        TaskRunProgressStream stream = stream(8, completed, timedOut, errored);
        stream.subscribe();
        stream.subscribe();
        stream.subscribe();

        completed.triggerCompletion();
        timedOut.triggerTimeout();
        errored.triggerError(new IOException("client disconnected"));

        assertThat(stream.subscriberCount()).isZero();
        stream.publish(event(1L));
        assertThat(completed.events()).isEmpty();
        assertThat(timedOut.events()).isEmpty();
        assertThat(errored.events()).isEmpty();
    }

    @Test
    void sendIOExceptionRemovesSubscriberWithoutApplicationCompletion() throws Exception {
        TestEmitter failing = new TestEmitter(EMITTER_TIMEOUT.toMillis());
        failing.failEvents = true;
        TestEmitter healthy = new TestEmitter(EMITTER_TIMEOUT.toMillis());
        TaskRunProgressStream stream = stream(8, failing, healthy);
        stream.subscribe();
        stream.subscribe();
        failing.awaitFrameCount(1);
        healthy.awaitFrameCount(1);

        stream.publish(event(1L));

        assertThat(failing.eventSendStarted.await(1, TimeUnit.SECONDS)).isTrue();
        healthy.awaitEventCount(1);
        awaitSubscriberCount(stream, 1);
        assertThat(failing.completeWithErrorCalls).hasValue(0);
        assertThat(failing.terminated.getCount()).isEqualTo(1L);
        assertThat(failing.terminalError.get()).isNull();
    }

    @Test
    void fullSlowQueueCannotStopHealthyOrLaterHeartbeats()
            throws Exception {
        TestEmitter slow = new TestEmitter(EMITTER_TIMEOUT.toMillis());
        slow.blockEvents = true;
        slow.ignoreSendInterrupts = true;
        TestEmitter healthy = new TestEmitter(EMITTER_TIMEOUT.toMillis());
        ScheduledExecutorService scheduler = mock(ScheduledExecutorService.class);
        TaskRunProgressStream stream = stream(1, scheduler, slow, healthy);
        stream.subscribe();
        stream.subscribe();
        slow.awaitFrameCount(1);
        healthy.awaitFrameCount(1);
        stream.publish(event(1L));
        assertThat(slow.eventSendStarted.await(1, TimeUnit.SECONDS)).isTrue();
        healthy.awaitEventCount(1);
        stream.publish(event(2L));
        healthy.awaitEventCount(2);

        ArgumentCaptor<Runnable> heartbeat = ArgumentCaptor.forClass(Runnable.class);
        verify(scheduler).scheduleAtFixedRate(
                heartbeat.capture(),
                eq(HEARTBEAT_INTERVAL.toMillis()),
                eq(HEARTBEAT_INTERVAL.toMillis()),
                eq(TimeUnit.MILLISECONDS));
        assertThatCode(heartbeat.getValue()::run).doesNotThrowAnyException();
        healthy.awaitFrameCount(4);
        slow.releaseEvents.countDown();
        slow.awaitEventCount(2);

        assertThatCode(heartbeat.getValue()::run).doesNotThrowAnyException();
        healthy.awaitFrameCount(5);
        slow.awaitFrameCount(4);
        assertThat(healthy.comments()).containsExactly("connected", "heartbeat", "heartbeat");
        assertThat(slow.comments()).containsExactly("connected", "heartbeat");
        assertThat(stream.subscriberCount()).isEqualTo(2);
    }

    @Test
    void shutdownCompletesSubscribersInterruptsDrainsAndStopsHeartbeatScheduler() throws Exception {
        TestEmitter emitter = new TestEmitter(EMITTER_TIMEOUT.toMillis());
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        TaskRunProgressStream stream = stream(8, scheduler, emitter);
        stream.subscribe();
        emitter.awaitFrameCount(1);
        emitter.blockEvents = true;
        emitter.ignoreSendInterrupts = true;
        stream.publish(event(1L));
        assertThat(emitter.eventSendStarted.await(1, TimeUnit.SECONDS)).isTrue();

        CountDownLatch closed = new CountDownLatch(1);
        Thread.startVirtualThread(() -> {
            stream.close();
            closed.countDown();
        });

        assertThat(closed.await(1, TimeUnit.SECONDS)).isTrue();
        assertThat(emitter.interrupted.await(1, TimeUnit.SECONDS)).isTrue();
        assertThat(stream.subscriberCount()).isZero();
        assertThat(scheduler.isShutdown()).isTrue();
        emitter.releaseEvents.countDown();
        assertThat(emitter.terminated.await(1, TimeUnit.SECONDS)).isTrue();
        assertThat(emitter.completionThread).hasValue(emitter.drainThread.get());
    }

    @Test
    void shutdownInterruptIOExceptionSuppressesLocalCompletion() throws Exception {
        TestEmitter emitter = new TestEmitter(EMITTER_TIMEOUT.toMillis());
        emitter.blockEvents = true;
        TaskRunProgressStream stream = stream(8, emitter);
        stream.subscribe();
        emitter.awaitFrameCount(1);
        stream.publish(event(1L));
        assertThat(emitter.eventSendStarted.await(1, TimeUnit.SECONDS)).isTrue();

        stream.close();

        assertThat(emitter.interrupted.await(1, TimeUnit.SECONDS)).isTrue();
        assertThat(emitter.eventSendExited.await(1, TimeUnit.SECONDS)).isTrue();
        assertThat(emitter.completionAttempted.await(300, TimeUnit.MILLISECONDS)).isFalse();
        assertThat(emitter.terminated.getCount()).isEqualTo(1L);
    }

    private TaskRunProgressStream stream(int queueCapacity, TestEmitter... emitters) {
        return stream(queueCapacity, Executors.newSingleThreadScheduledExecutor(), emitters);
    }

    private TaskRunProgressStream stream(
            int queueCapacity, ScheduledExecutorService scheduler, TestEmitter... emitters) {
        Queue<TestEmitter> available = new ConcurrentLinkedQueue<>(List.of(emitters));
        TaskRunProgressStream stream = new TaskRunProgressStream(
                () -> available.remove(), EMITTER_TIMEOUT, queueCapacity,
                HEARTBEAT_INTERVAL, scheduler);
        streams.add(stream);
        return stream;
    }

    private TaskRunProgressEvent event(long processedCount) {
        return new TaskRunProgressEvent(RUN_ID, "youtube", 10L, processedCount);
    }

    private TaskRunProgressEvent event(long processedCount, long totalCount) {
        return new TaskRunProgressEvent(RUN_ID, "youtube", totalCount, processedCount);
    }

    private void awaitSubscriberCount(TaskRunProgressStream stream, int expected) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(1);
        while (stream.subscriberCount() != expected && System.nanoTime() < deadline) {
            Thread.onSpinWait();
        }
        assertThat(stream.subscriberCount()).isEqualTo(expected);
    }

    private static final class TestEmitter extends SseEmitter {

        private final Queue<List<Object>> frames = new ConcurrentLinkedQueue<>();
        private final AtomicInteger eventCount = new AtomicInteger();
        private final AtomicInteger awaitedFrameCount = new AtomicInteger();
        private final AtomicInteger awaitedEventCount = new AtomicInteger();
        private final AtomicInteger completeWithErrorCalls = new AtomicInteger();
        private final Semaphore sentFrames = new Semaphore(0);
        private final Semaphore sentEvents = new Semaphore(0);
        private final ReentrantLock simulatedWriteLock = new ReentrantLock();
        private final CountDownLatch eventSendStarted = new CountDownLatch(1);
        private final CountDownLatch eventSendExited = new CountDownLatch(1);
        private final CountDownLatch releaseEvents = new CountDownLatch(1);
        private final CountDownLatch interrupted = new CountDownLatch(1);
        private final CountDownLatch terminated = new CountDownLatch(1);
        private final CountDownLatch completionAttempted = new CountDownLatch(1);
        private final AtomicReference<String> drainThread = new AtomicReference<>();
        private final AtomicReference<String> completionThread = new AtomicReference<>();
        private final AtomicBoolean virtualThread = new AtomicBoolean();
        private final AtomicReference<Throwable> terminalError = new AtomicReference<>();
        private volatile long expectedProcessedCount = -1L;
        private volatile CountDownLatch expectedProcessedCountDelivered = new CountDownLatch(1);
        private volatile Runnable completionCallback = () -> { };
        private volatile Runnable timeoutCallback = () -> { };
        private volatile Consumer<Throwable> errorCallback = error -> { };
        private volatile boolean blockEvents;
        private volatile boolean failEvents;
        private volatile boolean ignoreSendInterrupts;

        private TestEmitter(long timeout) {
            super(timeout);
        }

        @Override
        public void send(SseEventBuilder builder) throws IOException {
            boolean eventFrame = false;
            simulatedWriteLock.lock();
            try {
                List<Object> frame = builder.build().stream().map(DataWithMediaType::getData).toList();
                TaskRunProgressEvent event = frame.stream()
                        .filter(TaskRunProgressEvent.class::isInstance)
                        .map(TaskRunProgressEvent.class::cast)
                        .findFirst()
                        .orElse(null);
                drainThread.compareAndSet(null, Thread.currentThread().getName());
                virtualThread.set(Thread.currentThread().isVirtual());
                if (event != null) {
                    eventFrame = true;
                    eventSendStarted.countDown();
                    if (failEvents) {
                        throw new IOException("send failed");
                    }
                    if (blockEvents) {
                        awaitSendRelease();
                    }
                }
                frames.add(frame);
                sentFrames.release();
                if (event != null) {
                    eventCount.incrementAndGet();
                    sentEvents.release();
                    if (event.processedCount() == expectedProcessedCount) {
                        expectedProcessedCountDelivered.countDown();
                    }
                }
            } finally {
                if (eventFrame) {
                    eventSendExited.countDown();
                }
                simulatedWriteLock.unlock();
            }
        }

        private void awaitSendRelease() throws IOException {
            while (true) {
                try {
                    releaseEvents.await();
                    return;
                } catch (InterruptedException e) {
                    interrupted.countDown();
                    if (!ignoreSendInterrupts) {
                        Thread.currentThread().interrupt();
                        throw new IOException("send interrupted", e);
                    }
                }
            }
        }

        @Override
        public void onCompletion(Runnable callback) {
            completionCallback = callback;
        }

        @Override
        public void onTimeout(Runnable callback) {
            timeoutCallback = callback;
        }

        @Override
        public void onError(Consumer<Throwable> callback) {
            errorCallback = callback;
        }

        @Override
        public void complete() {
            completionThread.set(Thread.currentThread().getName());
            completionAttempted.countDown();
            simulatedWriteLock.lock();
            try {
                terminated.countDown();
            } finally {
                simulatedWriteLock.unlock();
            }
        }

        @Override
        public void completeWithError(Throwable error) {
            completionThread.set(Thread.currentThread().getName());
            completionAttempted.countDown();
            simulatedWriteLock.lock();
            try {
                completeWithErrorCalls.incrementAndGet();
                terminalError.compareAndSet(null, error);
                terminated.countDown();
            } finally {
                simulatedWriteLock.unlock();
            }
        }

        private void triggerCompletion() {
            completionCallback.run();
        }

        private void triggerTimeout() {
            timeoutCallback.run();
        }

        private void triggerError(Throwable error) {
            errorCallback.accept(error);
        }

        private void expectProcessedCount(long processedCount) {
            expectedProcessedCount = processedCount;
            expectedProcessedCountDelivered = new CountDownLatch(1);
        }

        private boolean awaitExpectedProcessedCount() throws InterruptedException {
            return expectedProcessedCountDelivered.await(1, TimeUnit.SECONDS);
        }

        private void awaitFrameCount(int expected) {
            int missing = expected - awaitedFrameCount.get();
            try {
                assertThat(sentFrames.tryAcquire(missing, 1, TimeUnit.SECONDS)).isTrue();
                awaitedFrameCount.set(expected);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new AssertionError(e);
            }
        }

        private void awaitEventCount(int expected) {
            int missing = expected - awaitedEventCount.get();
            try {
                assertThat(sentEvents.tryAcquire(missing, 1, TimeUnit.SECONDS)).isTrue();
                awaitedEventCount.set(expected);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new AssertionError(e);
            }
            assertThat(eventCount).hasValue(expected);
        }

        private List<TaskRunProgressEvent> events() {
            return frames.stream()
                    .flatMap(List::stream)
                    .filter(TaskRunProgressEvent.class::isInstance)
                    .map(TaskRunProgressEvent.class::cast)
                    .toList();
        }

        private List<String> comments() {
            return frames.stream()
                    .flatMap(List::stream)
                    .filter(String.class::isInstance)
                    .map(String.class::cast)
                    .filter(value -> value.startsWith(":"))
                    .map(value -> value.substring(1).trim())
                    .toList();
        }
    }
}
