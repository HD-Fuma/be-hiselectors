package com.fuma.hiselectors.selectors.service;

import com.fuma.hiselectors.application.model.SnsPlatform;
import com.fuma.hiselectors.exception.BusinessException;
import com.fuma.hiselectors.exception.ErrorCode;
import com.fuma.hiselectors.penalty.model.PenaltyHistory;
import com.fuma.hiselectors.penalty.model.PenaltyStatus;
import com.fuma.hiselectors.penalty.repository.PenaltyHistoryRepository;
import com.fuma.hiselectors.selectors.dto.SelectorsDetailResponse;
import com.fuma.hiselectors.selectors.dto.SelectorsGenerationResponse;
import com.fuma.hiselectors.selectors.dto.SelectorsPenaltyResponse;
import com.fuma.hiselectors.selectors.dto.SelectorsSnsAccountResponse;
import com.fuma.hiselectors.selectors.dto.SelectorsSummary;
import com.fuma.hiselectors.selectors.model.Selectors;
import com.fuma.hiselectors.selectors.model.SelectorsRole;
import com.fuma.hiselectors.selectors.model.SelectorsSnsAccount;
import com.fuma.hiselectors.selectors.repository.SelectorsGenerationRepository;
import com.fuma.hiselectors.selectors.repository.SelectorsRepository;
import com.fuma.hiselectors.selectors.repository.SelectorsRoleRepository;
import com.fuma.hiselectors.selectors.repository.SelectorsSnsAccountRepository;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 관리자용 셀렉터스 조회.
 *
 * <p>탈퇴·제명된 셀렉터스({@code is_deleted = true})는 어느 조회에도 나오지 않는다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SelectorsService {

    private static final long BLACKLIST_THRESHOLD = 3;

    private final SelectorsRepository selectorsRepository;
    private final SelectorsRoleRepository selectorsRoleRepository;
    private final SelectorsGenerationRepository selectorsGenerationRepository;
    private final SelectorsSnsAccountRepository selectorsSnsAccountRepository;
    private final PenaltyHistoryRepository penaltyHistoryRepository;

    /**
     * 조건에 맞는 셀렉터스 목록. null 인 조건은 적용하지 않는다.
     *
     * <p>대표 SNS 계정은 페이지에 걸린 셀렉터스들의 계정을 한 번에 조회한 뒤 메모리에서
     * 고른다. 셀렉터스마다 계정을 따로 조회하면 페이지 크기만큼 쿼리가 늘어난다.
     */
    public Page<SelectorsSummary> search(String roleId, Long generationId,
                                         String nickname, SnsPlatform snsCode,
                                         Pageable pageable) {
        Page<Selectors> page = selectorsRepository.search(
                blankToNull(roleId), generationId, blankToNull(nickname), snsCode, pageable);
        if (page.isEmpty()) {
            return page.map(selectors -> toSummary(selectors, null, Map.of()));
        }

        Map<String, String> roleNames = roleNames();
        Map<Long, SelectorsSnsAccount> representatives = representativeAccounts(
                page.getContent().stream().map(Selectors::getId).toList());

        return page.map(selectors ->
                toSummary(selectors, roleNames.get(selectors.getSelectorsRoleId()),
                        representatives));
    }

    /** 기본 정보와 참여 기수 이력, SNS 계정 전체를 함께 조회한다. */
    public SelectorsDetailResponse findDetail(Long selectorsId) {
        Selectors selectors = selectorsRepository.findByIdAndDeletedFalse(selectorsId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SELECTOR_NOT_FOUND));

        List<SelectorsGenerationResponse> generations =
                selectorsGenerationRepository.findGenerationsOf(selectorsId);
        List<SelectorsSnsAccountResponse> accounts = selectorsSnsAccountRepository
                .findAllBySelectorsIdAndDeletedFalseOrderByLastCollectedAtDescIdDesc(selectorsId)
                .stream()
                .map(SelectorsSnsAccountResponse::from)
                .toList();

        return SelectorsDetailResponse.of(
                selectors, roleNames().get(selectors.getSelectorsRoleId()),
                generations, accounts);
    }

    public Page<SelectorsPenaltyResponse> findPenalties(
            Long generationId, PenaltyStatus status, boolean blacklistOnly, Pageable pageable) {
        Page<Selectors> page = selectorsRepository.searchWithPenalties(
                generationId, status, blacklistOnly, BLACKLIST_THRESHOLD, pageable);
        if (page.isEmpty()) {
            return page.map(selectors -> SelectorsPenaltyResponse.of(
                    selectors, List.of(), BLACKLIST_THRESHOLD));
        }

        List<Long> selectorsIds = page.getContent().stream().map(Selectors::getId).toList();
        Map<Long, List<PenaltyHistory>> historiesBySelectorsId = penaltyHistoryRepository
                .findAllBySelectorsIds(selectorsIds).stream()
                .collect(Collectors.groupingBy(PenaltyHistory::getSelectorsId));

        return page.map(selectors -> SelectorsPenaltyResponse.of(
                selectors,
                historiesBySelectorsId.getOrDefault(selectors.getId(), List.of()),
                BLACKLIST_THRESHOLD));
    }

    private SelectorsSummary toSummary(Selectors selectors, String roleName,
                                       Map<Long, SelectorsSnsAccount> representatives) {
        SelectorsSnsAccount account = representatives.get(selectors.getId());
        return new SelectorsSummary(
                selectors.getId(),
                selectors.getSelectorsCode(),
                selectors.getSelectorsNickname(),
                selectors.getSelectorsRoleId(),
                roleName,
                account == null || account.getSnsCode() == null
                        ? null : account.getSnsCode().name(),
                account == null ? null : account.getAccountId(),
                account == null ? null : account.getFollowerCount(),
                account == null ? null : account.getProfileImageUrl(),
                selectors.getCreatedAt()
        );
    }

    private Map<String, String> roleNames() {
        return selectorsRoleRepository.findAll().stream()
                .collect(Collectors.toMap(SelectorsRole::getId, SelectorsRole::getRoleName));
    }

    /**
     * 셀렉터스별 대표 SNS 계정. 가장 최근에 수집된 계정을 쓰고, 수집 시각이 없으면
     * 가장 나중에 등록된 계정을 쓴다.
     */
    private Map<Long, SelectorsSnsAccount> representativeAccounts(List<Long> selectorsIds) {
        Comparator<SelectorsSnsAccount> latest = Comparator
                .comparing(SelectorsSnsAccount::getLastCollectedAt,
                        Comparator.nullsFirst(Comparator.naturalOrder()))
                .thenComparing(SelectorsSnsAccount::getId);

        return selectorsSnsAccountRepository
                .findAllBySelectorsIdInAndDeletedFalse(selectorsIds).stream()
                .collect(Collectors.toMap(
                        SelectorsSnsAccount::getSelectorsId,
                        Function.identity(),
                        (left, right) -> latest.compare(left, right) >= 0 ? left : right));
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
