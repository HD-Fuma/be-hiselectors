package com.fuma.hiselectors.generation.service;

import com.fuma.hiselectors.exception.BusinessException;
import com.fuma.hiselectors.exception.ErrorCode;
import com.fuma.hiselectors.generation.model.Generation;
import com.fuma.hiselectors.generation.model.GenerationStatus;
import com.fuma.hiselectors.generation.repository.GenerationRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GenerationService {

    private final GenerationRepository generationRepository;
    private final Clock clock;

    public Generation getActive() {
        LocalDateTime now = LocalDateTime.now(clock);
        return generationRepository
                .findFirstByStartDateLessThanEqualAndEndDateGreaterThanEqualAndStatusOrderByStartDateAsc(
                        now, now, GenerationStatus.ACTIVE)
                .orElseThrow(() -> new BusinessException(ErrorCode.ACTIVE_GENERATION_NOT_FOUND));
    }
}
