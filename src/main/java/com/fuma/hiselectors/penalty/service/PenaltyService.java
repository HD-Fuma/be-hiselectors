package com.fuma.hiselectors.penalty.service;

import com.fuma.hiselectors.admin.model.Admin;
import com.fuma.hiselectors.admin.repository.AdminRepository;
import com.fuma.hiselectors.exception.BusinessException;
import com.fuma.hiselectors.exception.ErrorCode;
import com.fuma.hiselectors.inspection.model.ViolationStatus;
import com.fuma.hiselectors.inspection.repository.ViolationItemRepository;
import com.fuma.hiselectors.inspection.repository.ViolationTypeRepository;
import com.fuma.hiselectors.penalty.dto.PenaltyCreateRequest;
import com.fuma.hiselectors.penalty.model.PenaltyHistory;
import com.fuma.hiselectors.penalty.model.PenaltySource;
import com.fuma.hiselectors.penalty.model.PenaltyStatus;
import com.fuma.hiselectors.penalty.repository.PenaltyHistoryRepository;
import com.fuma.hiselectors.selectors.dto.PenaltyHistoryResponse;
import com.fuma.hiselectors.selectors.model.BlacklistHistory;
import com.fuma.hiselectors.selectors.model.Selectors;
import com.fuma.hiselectors.selectors.dto.SelectorsGenerationResponse;
import com.fuma.hiselectors.selectors.repository.SelectorsGenerationRepository;
import com.fuma.hiselectors.selectors.repository.SelectorsRepository;
import com.fuma.hiselectors.selectors.repository.BlacklistHistoryRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PenaltyService {

    private static final long BLACKLIST_THRESHOLD = 3;
    private static final String BLACKLIST_REASON =
            "패널티 누적 3회로 인한 자동 블랙리스트 전환";
    private static final Set<ViolationStatus> OPEN_STATUSES = EnumSet.of(
            ViolationStatus.PENDING,
            ViolationStatus.VIOLATION_CONFIRMED,
            ViolationStatus.EDIT_REQUESTED);

    private final SelectorsRepository selectorsRepository;
    private final BlacklistHistoryRepository blacklistHistoryRepository;
    private final AdminRepository adminRepository;
    private final SelectorsGenerationRepository selectorsGenerationRepository;
    private final PenaltyHistoryRepository penaltyHistoryRepository;
    private final ViolationTypeRepository violationTypeRepository;
    private final ViolationItemRepository violationItemRepository;
    private final Clock clock;

    @Transactional
    public PenaltyHistoryResponse create(Long selectorsId, PenaltyCreateRequest request,
                                         String adminLoginId) {
        if (!violationTypeRepository.existsById(request.violationTypeId())) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        Selectors selectors = requireSelectorsForUpdate(selectorsId);
        if (selectors.isBlacklisted()) {
            throw new BusinessException(ErrorCode.BLACKLISTED_SELECTOR);
        }
        if (findActivePenalty(selectorsId).isPresent()) {
            throw new BusinessException(ErrorCode.ACTIVE_PENALTY_ALREADY_EXISTS);
        }
        Admin admin = requireAdmin(adminLoginId);
        return PenaltyHistoryResponse.from(activate(
                selectors, null, request.violationTypeId(), request.reason().trim(),
                PenaltySource.MANUAL, admin.getId(), LocalDateTime.now(clock)));
    }

    /** 위반 확정 시 활성 패널티가 없을 때만 새 패널티 주기를 시작한다. */
    @Transactional
    public boolean activateIfAbsent(Long selectorsId, Long contentVersionId,
                                    Long violationTypeId, String reason,
                                    String adminLoginId) {
        Selectors selectors = requireSelectorsForUpdate(selectorsId);
        if (selectors.isBlacklisted() || findActivePenalty(selectorsId).isPresent()) {
            return false;
        }
        Admin admin = requireAdmin(adminLoginId);
        activate(selectors, contentVersionId, violationTypeId, reason,
                PenaltySource.AUTOMATIC, admin.getId(), LocalDateTime.now(clock));
        return true;
    }

    @Transactional
    public PenaltyHistoryResponse releaseManually(Long selectorsId, Long penaltyHistoryId,
                                                  String adminLoginId) {
        Admin admin = requireAdmin(adminLoginId);
        requireSelectorsForUpdate(selectorsId);
        PenaltyHistory penalty = penaltyHistoryRepository
                .findByIdAndSelectorsIdForUpdate(penaltyHistoryId, selectorsId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PENALTY_NOT_FOUND));
        if (penalty.getStatus() != PenaltyStatus.ACTIVE) {
            throw new BusinessException(ErrorCode.INVALID_PENALTY_STATUS_TRANSITION);
        }
        penalty.releaseByAdmin(admin.getId(), LocalDateTime.now(clock));
        return PenaltyHistoryResponse.from(penalty);
    }

    /** 열린 위반이 모두 해소되면 현재 패널티 주기를 종료한다. */
    @Transactional
    public boolean releaseIfEligible(Long selectorsId) {
        if (violationItemRepository.existsOpenBySelectorsId(selectorsId, OPEN_STATUSES)) {
            return false;
        }
        requireSelectorsForUpdate(selectorsId);
        return findActivePenalty(selectorsId)
                .filter(penalty -> penalty.getSource() != PenaltySource.MANUAL)
                .map(penalty -> {
            penalty.release(LocalDateTime.now(clock));
            return true;
        }).orElse(false);
    }

    private PenaltyHistory activate(Selectors selectors, Long contentVersionId,
                                    Long violationTypeId, String reason, PenaltySource source,
                                    Long grantedByAdminId, LocalDateTime now) {
        SelectorsGenerationResponse generation = currentGeneration(selectors.getId(), now);
        PenaltyHistory saved = penaltyHistoryRepository.saveAndFlush(PenaltyHistory.activate(
                selectors.getId(), generation.generationId(), contentVersionId,
                violationTypeId, reason, source, grantedByAdminId, now));
        long accumulatedPenaltyCount = penaltyHistoryRepository.countBySelectorsId(
                selectors.getId());
        if (accumulatedPenaltyCount >= BLACKLIST_THRESHOLD && !selectors.isBlacklisted()) {
            selectors.blacklist();
            if (!blacklistHistoryRepository.existsBySelectorsIdAndStatus(
                    selectors.getId(), BlacklistHistory.ACTIVE_STATUS)) {
                blacklistHistoryRepository.save(BlacklistHistory.activate(
                        selectors.getId(), BLACKLIST_REASON));
            }
        }
        return saved;
    }

    private Selectors requireSelectorsForUpdate(Long selectorsId) {
        return selectorsRepository.findByIdForUpdate(selectorsId)
                .filter(value -> !value.isDeleted())
                .orElseThrow(() -> new BusinessException(ErrorCode.SELECTOR_NOT_FOUND));
    }

    private Admin requireAdmin(String adminLoginId) {
        return adminRepository.findByLoginId(adminLoginId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ADMIN_NOT_FOUND));
    }

    private java.util.Optional<PenaltyHistory> findActivePenalty(Long selectorsId) {
        return penaltyHistoryRepository.findFirstBySelectorsIdAndStatusOrderByIdDesc(
                selectorsId, PenaltyStatus.ACTIVE);
    }

    private SelectorsGenerationResponse currentGeneration(Long selectorsId, LocalDateTime now) {
        return selectorsGenerationRepository.findGenerationsOf(selectorsId).stream()
                .filter(value -> value.joinedAt() != null
                        && value.activityStartDate() != null
                        && value.activityEndDate() != null
                        && !value.joinedAt().isAfter(now)
                        && !value.activityStartDate().isAfter(now)
                        && !value.activityEndDate().isBefore(now))
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.ACCESS_DENIED));
    }
}
