package com.fuma.hiselectors.logging;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicBoolean;

public final class BatchLogContext {

    private final String batch;
    private final String runId;
    private final Instant startedAt;
    private final AtomicBoolean terminal = new AtomicBoolean();

    BatchLogContext(String batch, String runId, Instant startedAt) {
        this.batch = batch;
        this.runId = runId;
        this.startedAt = startedAt;
    }

    String batch() {
        return batch;
    }

    String runId() {
        return runId;
    }

    Instant startedAt() {
        return startedAt;
    }

    void markTerminal() {
        if (!terminal.compareAndSet(false, true)) {
            throw new IllegalStateException("Batch run already has a terminal event");
        }
    }
}
