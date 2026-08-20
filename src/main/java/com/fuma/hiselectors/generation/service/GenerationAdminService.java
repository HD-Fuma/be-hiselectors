package com.fuma.hiselectors.generation.service;

import com.fuma.hiselectors.exception.BusinessException;
import com.fuma.hiselectors.exception.ErrorCode;
import com.fuma.hiselectors.generation.dto.GenerationCreateRequest;
import com.fuma.hiselectors.generation.dto.GenerationResponse;
import com.fuma.hiselectors.generation.dto.GenerationStatusUpdateRequest;
import com.fuma.hiselectors.generation.dto.GenerationUpdateRequest;
import com.fuma.hiselectors.generation.model.Generation;
import com.fuma.hiselectors.generation.model.GenerationStatus;
import com.fuma.hiselectors.generation.repository.GenerationRepository;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GenerationAdminService {

    private final GenerationRepository generationRepository;

    public List<GenerationResponse> findAll() {
        return generationRepository.findAllByOrderByStartDateDescIdDesc().stream()
                .map(GenerationResponse::from)
                .toList();
    }

    public GenerationResponse findOne(Long generationId) {
        return GenerationResponse.from(getGeneration(generationId));
    }

    @Transactional
    public GenerationResponse create(GenerationCreateRequest request) {
        validatePeriod(request.startDate(), request.endDate());

        Generation generation = generationRepository.save(Generation.builder()
                .generationName(request.generationName().trim())
                .startDate(request.startDate())
                .endDate(request.endDate())
                .status(GenerationStatus.INACTIVE)
                .build());
        return GenerationResponse.from(generation);
    }

    @Transactional
    public GenerationResponse update(Long generationId, GenerationUpdateRequest request) {
        Generation generation = getGeneration(generationId);
        String name = request.generationName() == null ? null : request.generationName().trim();
        LocalDateTime startDate = request.startDate() == null
                ? generation.getStartDate() : request.startDate();
        LocalDateTime endDate = request.endDate() == null
                ? generation.getEndDate() : request.endDate();

        validatePeriod(startDate, endDate);
        if (generation.getStatus() == GenerationStatus.ACTIVE) {
            validateNoActiveOverlap(startDate, endDate, generationId);
        }

        generation.update(name, request.startDate(), request.endDate());
        return GenerationResponse.from(generation);
    }

    @Transactional
    public GenerationResponse updateStatus(
            Long generationId, GenerationStatusUpdateRequest request) {
        Generation generation = getGeneration(generationId);
        if (request.status() == GenerationStatus.ACTIVE) {
            validateNoActiveOverlap(
                    generation.getStartDate(), generation.getEndDate(), generationId);
        }

        generation.changeStatus(request.status());
        return GenerationResponse.from(generation);
    }

    private Generation getGeneration(Long generationId) {
        return generationRepository.findById(generationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.GENERATION_NOT_FOUND));
    }

    private void validatePeriod(LocalDateTime startDate, LocalDateTime endDate) {
        if (!startDate.isBefore(endDate)) {
            throw new BusinessException(ErrorCode.GENERATION_PERIOD_INVALID);
        }
    }

    private void validateNoActiveOverlap(
            LocalDateTime startDate, LocalDateTime endDate, Long excludedId) {
        if (generationRepository.existsOverlapping(
                startDate, endDate, GenerationStatus.ACTIVE, excludedId)) {
            throw new BusinessException(ErrorCode.ACTIVE_GENERATION_OVERLAPPED);
        }
    }
}
