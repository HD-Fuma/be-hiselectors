package com.fuma.hiselectors.selectors.service;

import com.fuma.hiselectors.exception.BusinessException;
import com.fuma.hiselectors.exception.ErrorCode;
import com.fuma.hiselectors.selectors.dto.SelectorAccessResponse;
import com.fuma.hiselectors.selectors.dto.SelectorsGenerationResponse;
import com.fuma.hiselectors.selectors.model.SelectorAccessLevel;
import com.fuma.hiselectors.selectors.model.Selectors;
import com.fuma.hiselectors.selectors.repository.SelectorsGenerationRepository;
import com.fuma.hiselectors.selectors.repository.SelectorsRepository;
import com.fuma.hiselectors.user.model.User;
import com.fuma.hiselectors.user.repository.UserRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SelectorAccessService {

    private final UserRepository userRepository;
    private final SelectorsRepository selectorsRepository;
    private final SelectorsGenerationRepository selectorsGenerationRepository;
    private final Clock clock;

    public SelectorAccessResponse getAccess(String loginId) {
        return resolve(loginId).response();
    }

    public Selectors requireReadable(String loginId) {
        return require(loginId, SelectorAccessLevel.CURRENT, SelectorAccessLevel.PREVIOUS);
    }

    public Selectors requireReadable(Selectors selectors) {
        return require(resolve(selectors),
                SelectorAccessLevel.CURRENT, SelectorAccessLevel.PREVIOUS);
    }

    public Selectors requireCurrent(String loginId) {
        return require(loginId, SelectorAccessLevel.CURRENT);
    }

    public Selectors requireCurrent(Selectors selectors) {
        return require(resolve(selectors), SelectorAccessLevel.CURRENT);
    }

    public Selectors requireSettlementHistoryReadable(String loginId) {
        return require(loginId, SelectorAccessLevel.CURRENT, SelectorAccessLevel.PREVIOUS,
                SelectorAccessLevel.BLACKLIST);
    }

    private Selectors require(String loginId, SelectorAccessLevel... allowed) {
        return require(resolve(loginId), allowed);
    }

    private Selectors require(ResolvedAccess resolved, SelectorAccessLevel... allowed) {
        for (SelectorAccessLevel level : allowed) {
            if (resolved.response().accessLevel() == level) {
                return resolved.selectors();
            }
        }
        throw new BusinessException(ErrorCode.ACCESS_DENIED);
    }

    private ResolvedAccess resolve(String loginId) {
        User user = userRepository.findByHiId(loginId)
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
        Selectors selectors = selectorsRepository.findByUserId(user.getId())
                .filter(value -> !value.isDeleted())
                .orElse(null);
        return resolve(selectors);
    }

    private ResolvedAccess resolve(Selectors selectors) {
        if (selectors == null || selectors.isDeleted()) {
            return new ResolvedAccess(null,
                    SelectorAccessResponse.of(SelectorAccessLevel.NONE, null,
                            (SelectorsGenerationResponse) null));
        }

        List<SelectorsGenerationResponse> generations = selectorsGenerationRepository
                .findGenerationsOf(selectors.getId());
        SelectorsGenerationResponse latest = generations.isEmpty() ? null : generations.getFirst();
        if (selectors.isBlacklisted()) {
            return new ResolvedAccess(selectors,
                    SelectorAccessResponse.of(
                            SelectorAccessLevel.BLACKLIST, selectors.getId(), latest));
        }

        LocalDateTime now = LocalDateTime.now(clock);
        SelectorsGenerationResponse current = selectors.isActive()
                ? generations.stream().filter(generation ->
                        generation.joinedAt() != null
                                && generation.activityEndDate() != null
                                && !generation.joinedAt().isAfter(now)
                                && !generation.activityEndDate().isBefore(now))
                .findFirst().orElse(null)
                : null;
        if (current != null) {
            return new ResolvedAccess(selectors,
                    SelectorAccessResponse.of(
                            SelectorAccessLevel.CURRENT, selectors.getId(), current));
        }

        LocalDateTime cutoff = now.minusYears(1);
        SelectorsGenerationResponse previous = generations.stream().filter(generation ->
                        generation.activityEndDate() != null
                                && generation.activityEndDate().isBefore(now)
                                && !generation.activityEndDate().isBefore(cutoff))
                .findFirst().orElse(null);
        SelectorAccessLevel level = previous == null
                ? SelectorAccessLevel.NONE : SelectorAccessLevel.PREVIOUS;
        return new ResolvedAccess(selectors,
                SelectorAccessResponse.of(level, selectors.getId(), previous));
    }

    private record ResolvedAccess(Selectors selectors, SelectorAccessResponse response) {
    }
}
