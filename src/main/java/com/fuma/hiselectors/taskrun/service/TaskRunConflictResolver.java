package com.fuma.hiselectors.taskrun.service;

import com.fuma.hiselectors.exception.BusinessException;
import com.fuma.hiselectors.exception.ErrorCode;
import com.fuma.hiselectors.taskrun.model.TaskRun;
import com.fuma.hiselectors.taskrun.repository.TaskRunRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TaskRunConflictResolver {

    private final TaskRunRepository repository;

    public TaskRunConflictResolver(TaskRunRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
    public TaskStartResult resolve(
            TaskStartCommand command,
            String fingerprint,
            String concurrencyKey,
            DataIntegrityViolationException originalConflict) {
        TaskRun sameRequest = repository.findByIdempotencyKey(command.idempotencyKey()).orElse(null);
        if (sameRequest != null) {
            if (!sameRequest.getRequestFingerprint().equals(fingerprint)) {
                throw new BusinessException(ErrorCode.IDEMPOTENCY_KEY_REUSED);
            }
            return new TaskStartResult.Replayed(sameRequest);
        }

        if (concurrencyKey != null) {
            return repository.findByConcurrencyKey(concurrencyKey)
                    .<TaskStartResult>map(TaskStartResult.ActiveConflict::new)
                    .orElseThrow(() -> originalConflict);
        }
        throw originalConflict;
    }
}
