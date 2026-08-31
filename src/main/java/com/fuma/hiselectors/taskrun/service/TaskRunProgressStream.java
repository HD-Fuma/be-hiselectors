package com.fuma.hiselectors.taskrun.service;

import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.time.Duration;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/** 단일 JVM 안에서 TaskRun 진행률을 관리자 SSE 구독자에게 전달한다. */
@Component
@Slf4j
public class TaskRunProgressStream implements AutoCloseable {

    private static final Duration DEFAULT_TIMEOUT = Duration.ofMinutes(30);
    private static final Duration DEFAULT_HEARTBEAT_INTERVAL = Duration.ofSeconds(15);
    private static final int DEFAULT_QUEUE_CAPACITY = 256;

    private final Supplier<SseEmitter> emitterFactory;
    private final int queueCapacity;
    private final ScheduledExecutorService heartbeatScheduler;
    private final Set<Subscriber> subscribers = new CopyOnWriteArraySet<>();
    private final AtomicLong subscriberSequence = new AtomicLong();
    private final AtomicBoolean closed = new AtomicBoolean();

    public TaskRunProgressStream() {
        this(
                () -> new SseEmitter(DEFAULT_TIMEOUT.toMillis()),
                DEFAULT_TIMEOUT,
                DEFAULT_QUEUE_CAPACITY,
                DEFAULT_HEARTBEAT_INTERVAL,
                newHeartbeatScheduler());
    }

    TaskRunProgressStream(
            Supplier<SseEmitter> emitterFactory,
            Duration emitterTimeout,
            int queueCapacity,
            Duration heartbeatInterval,
            ScheduledExecutorService heartbeatScheduler) {
        this.emitterFactory = Objects.requireNonNull(emitterFactory, "SSE 생성기는 필수입니다.");
        requirePositive(emitterTimeout, "SSE 제한 시간은 양수여야 합니다.");
        if (queueCapacity <= 0) {
            throw new IllegalArgumentException("SSE 큐 크기는 양수여야 합니다.");
        }
        requirePositive(heartbeatInterval, "SSE 하트비트 주기는 양수여야 합니다.");
        this.queueCapacity = queueCapacity;
        this.heartbeatScheduler = Objects.requireNonNull(
                heartbeatScheduler, "SSE 하트비트 스케줄러는 필수입니다.");
        long heartbeatMillis = heartbeatInterval.toMillis();
        heartbeatScheduler.scheduleAtFixedRate(
                this::heartbeatSafely, heartbeatMillis, heartbeatMillis, TimeUnit.MILLISECONDS);
    }

    public SseEmitter subscribe() {
        if (closed.get()) {
            throw new IllegalStateException("SSE 스트림이 종료되었습니다.");
        }

        SseEmitter emitter = Objects.requireNonNull(emitterFactory.get(), "SSE 생성 결과는 필수입니다.");
        Subscriber subscriber = new Subscriber(emitter, queueCapacity);
        emitter.onCompletion(subscriber::clientTerminated);
        emitter.onTimeout(subscriber::clientTerminated);
        emitter.onError(error -> subscriber.clientTerminated());
        subscriber.enqueue(OutboundMessage.comment("connected"));
        subscribers.add(subscriber);
        subscriber.start();
        if (closed.get()) {
            subscriber.shutdown();
            throw new IllegalStateException("SSE 스트림이 종료되었습니다.");
        }
        return emitter;
    }

    public synchronized void publish(TaskRunProgressEvent event) {
        Objects.requireNonNull(event, "진행 이벤트는 필수입니다.");
        if (closed.get()) {
            return;
        }
        OutboundMessage message = OutboundMessage.event(event);
        fanOut(message, "진행 이벤트");
    }

    public synchronized void publishChanged(UUID runId) {
        Objects.requireNonNull(runId, "실행 ID는 필수입니다.");
        if (closed.get()) {
            return;
        }
        fanOut(OutboundMessage.changed(runId), "상태 변경 이벤트");
    }

    void heartbeat() {
        if (closed.get()) {
            return;
        }
        OutboundMessage message = OutboundMessage.comment("heartbeat");
        fanOut(message, "하트비트");
    }

    int subscriberCount() {
        return (int) subscribers.stream().filter(Subscriber::isActive).count();
    }

    public boolean hasSubscribers() {
        return subscriberCount() > 0;
    }

    @Override
    @PreDestroy
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        heartbeatScheduler.shutdownNow();
        subscribers.forEach(subscriber -> {
            try {
                subscriber.shutdown();
            } catch (RuntimeException failure) {
                log.warn("TaskRun SSE 구독자 종료 처리 실패: subscriber={}",
                        subscriber.id, failure);
            }
        });
    }

    private void heartbeatSafely() {
        try {
            heartbeat();
        } catch (RuntimeException failure) {
            log.warn("TaskRun SSE 하트비트 실행 실패", failure);
        }
    }

    private void fanOut(OutboundMessage message, String messageType) {
        subscribers.forEach(subscriber -> {
            try {
                subscriber.enqueue(message);
            } catch (RuntimeException failure) {
                log.warn("TaskRun SSE {} 전달 준비 실패: subscriber={}",
                        messageType, subscriber.id, failure);
                try {
                    subscriber.discard();
                } catch (RuntimeException cleanupFailure) {
                    log.warn("TaskRun SSE 구독자 정리 실패: subscriber={}",
                            subscriber.id, cleanupFailure);
                }
            }
        });
    }

    private static ScheduledExecutorService newHeartbeatScheduler() {
        return Executors.newSingleThreadScheduledExecutor(
                Thread.ofPlatform().daemon().name("task-run-progress-heartbeat").factory());
    }

    private static void requirePositive(Duration duration, String message) {
        Objects.requireNonNull(duration, message);
        if (duration.isZero() || duration.isNegative() || duration.toMillis() == 0) {
            throw new IllegalArgumentException(message);
        }
    }

    private final class Subscriber {

        private final SseEmitter emitter;
        private final ArrayBlockingQueue<OutboundMessage> queue;
        private final AtomicReference<TerminalAction> terminalAction =
                new AtomicReference<>(TerminalAction.ACTIVE);
        private final long id = subscriberSequence.incrementAndGet();
        private volatile Thread drainThread;

        private Subscriber(SseEmitter emitter, int capacity) {
            this.emitter = emitter;
            this.queue = new ArrayBlockingQueue<>(capacity);
        }

        private void start() {
            Thread thread = Thread.ofVirtual()
                    .name("task-run-progress-" + id)
                    .unstarted(this::drain);
            drainThread = thread;
            thread.start();
        }

        private void enqueue(OutboundMessage message) {
            if (!isActive()) {
                return;
            }
            synchronized (queue) {
                if (queue.offer(message)) {
                    return;
                }
                if (message instanceof ProgressMessage latest) {
                    queue.removeIf(queued -> queued instanceof ProgressMessage progress
                            && progress.hasSameKey(latest));
                    if (!queue.offer(message)) {
                        queue.poll();
                        queue.offer(message);
                    }
                } else if (message instanceof ChangeMessage) {
                    queue.removeIf(ChangeMessage.class::isInstance);
                    if (!queue.offer(message)) {
                        queue.poll();
                        queue.offer(message);
                    }
                }
            }
        }

        private void drain() {
            try {
                while (isActive()) {
                    queue.take().send(emitter);
                }
            } catch (InterruptedException interrupted) {
                discard();
            } catch (IOException failure) {
                discardAfterSendIOException();
                log.debug("TaskRun SSE 전송 연결 종료: subscriber={}", id, failure);
            } catch (RuntimeException failure) {
                discard();
                log.warn("TaskRun SSE 전송 실패: subscriber={}", id, failure);
            } finally {
                finishTerminalAction();
            }
        }

        private void clientTerminated() {
            discard();
        }

        private void shutdown() {
            requestTerminal(TerminalAction.COMPLETE);
        }

        private void discard() {
            requestTerminal(TerminalAction.DISCARD);
        }

        private void discardAfterSendIOException() {
            terminalAction.set(TerminalAction.DISCARD);
            queue.clear();
        }

        private boolean isActive() {
            return terminalAction.get() == TerminalAction.ACTIVE;
        }

        private void requestTerminal(TerminalAction requestedAction) {
            if (!terminalAction.compareAndSet(TerminalAction.ACTIVE, requestedAction)) {
                return;
            }
            queue.clear();
            Thread currentDrain = drainThread;
            if (currentDrain != null) {
                currentDrain.interrupt();
            }
        }

        private void finishTerminalAction() {
            TerminalAction action = terminalAction.get();
            try {
                if (action == TerminalAction.COMPLETE) {
                    emitter.complete();
                }
            } catch (RuntimeException completionFailure) {
                log.warn("TaskRun SSE 구독자 완료 신호 실패: subscriber={}",
                        id, completionFailure);
            } finally {
                queue.clear();
                drainThread = null;
                subscribers.remove(this);
            }
        }
    }

    private enum TerminalAction {
        ACTIVE,
        DISCARD,
        COMPLETE
    }

    private sealed interface OutboundMessage permits CommentMessage, ProgressMessage, ChangeMessage {

        void send(SseEmitter emitter) throws IOException;

        static OutboundMessage comment(String comment) {
            return new CommentMessage(comment);
        }

        static OutboundMessage event(TaskRunProgressEvent event) {
            return new ProgressMessage(event);
        }

        static OutboundMessage changed(UUID runId) {
            return new ChangeMessage(runId);
        }
    }

    private record CommentMessage(String comment) implements OutboundMessage {

        @Override
        public void send(SseEmitter emitter) throws IOException {
            emitter.send(SseEmitter.event().comment(comment));
        }
    }

    private record ProgressMessage(TaskRunProgressEvent event) implements OutboundMessage {

        private boolean hasSameKey(ProgressMessage other) {
            return event.runId().equals(other.event.runId())
                    && event.stepKey().equals(other.event.stepKey());
        }

        @Override
        public void send(SseEmitter emitter) throws IOException {
            emitter.send(SseEmitter.event()
                    .name("task-run-progress")
                    .data(event));
        }
    }

    private record ChangeMessage(UUID runId) implements OutboundMessage {

        @Override
        public void send(SseEmitter emitter) throws IOException {
            emitter.send(SseEmitter.event()
                    .name("task-run-changed")
                    .data(runId.toString()));
        }
    }
}
