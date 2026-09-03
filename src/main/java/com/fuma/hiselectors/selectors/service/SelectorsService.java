package com.fuma.hiselectors.selectors.service;

import com.fuma.hiselectors.application.model.Application;
import com.fuma.hiselectors.application.model.SnsPlatform;
import com.fuma.hiselectors.application.repository.ApplicationRepository;
import com.fuma.hiselectors.content.client.YoutubeContentFetcher;
import com.fuma.hiselectors.content.model.Content;
import com.fuma.hiselectors.content.model.ContentEngagement;
import com.fuma.hiselectors.content.model.ContentMedia;
import com.fuma.hiselectors.content.model.ContentVersion;
import com.fuma.hiselectors.content.model.MediaType;
import com.fuma.hiselectors.content.repository.ContentEngagementRepository;
import com.fuma.hiselectors.content.repository.ContentMediaRepository;
import com.fuma.hiselectors.content.repository.ContentRepository;
import com.fuma.hiselectors.content.repository.ContentVersionRepository;
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
import com.fuma.hiselectors.user.model.User;
import com.fuma.hiselectors.user.repository.UserRepository;
import java.util.HashMap;
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
    private final YoutubeContentFetcher youtubeContentFetcher;
    private final PenaltyHistoryRepository penaltyHistoryRepository;
    private final ApplicationRepository applicationRepository;
    private final UserRepository userRepository;
    private final ContentRepository contentRepository;
    private final ContentEngagementRepository contentEngagementRepository;
    private final ContentVersionRepository contentVersionRepository;
    private final ContentMediaRepository contentMediaRepository;

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
            return page.map(selectors -> toSummary(selectors, null, Map.of(), Map.of()));
        }

        Map<String, String> roleNames = roleNames();
        Map<Long, SelectorsSnsAccount> representatives = representativeAccounts(
                page.getContent().stream().map(Selectors::getId).toList());
        Map<String, String> youtubeTitles = youtubeChannelTitles(representatives);

        return page.map(selectors ->
                toSummary(selectors, roleNames.get(selectors.getSelectorsRoleId()),
                        representatives, youtubeTitles));
    }

    /** 기본 정보와 참여 기수 이력, SNS 계정을 함께 조회한다. */
    public SelectorsDetailResponse findDetail(Long selectorsId) {
        Selectors selectors = selectorsRepository.findByIdAndDeletedFalse(selectorsId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SELECTOR_NOT_FOUND));

        List<SelectorsGenerationResponse> generations =
                selectorsGenerationRepository.findGenerationsOf(selectorsId);
        SelectorsSnsAccount accountEntity = selectorsSnsAccountRepository
                .findBySelectorsIdAndDeletedFalse(selectorsId)
                .orElse(null);
        Map<String, String> youtubeTitles = accountEntity == null
                ? Map.of() : youtubeChannelTitles(Map.of(selectorsId, accountEntity));
        SelectorsSnsAccountResponse account = accountEntity == null
                ? null : SelectorsSnsAccountResponse.from(
                        accountEntity, snsDisplayName(accountEntity, youtubeTitles));
        List<PenaltyHistory> penalties = penaltyHistoryRepository
                .findAllBySelectorsIds(List.of(selectorsId));
        Application application = selectors.getApplicationId() == null
                ? null : applicationRepository.findById(selectors.getApplicationId()).orElse(null);
        User user = selectors.getUserId() == null
                ? null : userRepository.findById(selectors.getUserId()).orElse(null);

        // ponytail: 상세 조회에서 전체 콘텐츠를 합산한다. 건수가 커져 병목이면 DB 집계로 교체한다.
        List<Content> contents = contentRepository
                .findAllBySelectorsIdAndDeletedFalseOrderByCreatedAtDescIdDesc(selectorsId);
        Map<Long, String> contentTitles = contentTitles(contents.stream().limit(5).toList());
        Map<Long, ContentEngagement> latestEngagements = contents.isEmpty()
                ? Map.of()
                : contentEngagementRepository.findLatestByContentIds(
                                contents.stream().map(Content::getId).toList()).stream()
                        .collect(Collectors.toMap(
                                ContentEngagement::getContentId, Function.identity()));

        return SelectorsDetailResponse.of(
                selectors, roleNames().get(selectors.getSelectorsRoleId()),
                generations, account, application, user, penalties, contents,
                latestEngagements, contentTitles,
                BLACKLIST_THRESHOLD);
    }

    private Map<Long, String> contentTitles(List<Content> contents) {
        if (contents.isEmpty()) {
            return Map.of();
        }
        List<ContentVersion> versions = contentVersionRepository.findCurrentByContentIdIn(
                contents.stream().map(Content::getId).toList());
        Map<Long, Long> contentIdsByVersionId = versions.stream().collect(Collectors.toMap(
                ContentVersion::getId, ContentVersion::getContentId));
        if (contentIdsByVersionId.isEmpty()) {
            return Map.of();
        }

        Map<Long, String> titles = new HashMap<>();
        List<ContentMedia> media = contentMediaRepository
                .findAllByContentVersionIdInOrderByContentVersionIdAscSequenceNoAsc(
                        contentIdsByVersionId.keySet());
        for (ContentMedia item : media) {
            Object text = item.bodyOrEmpty().get("text");
            Long contentId = contentIdsByVersionId.get(item.getContentVersionId());
            if (item.getMediaType() == MediaType.TEXT
                    && text instanceof String title
                    && !title.isBlank()
                    && contentId != null) {
                titles.putIfAbsent(contentId, title.trim());
            }
        }
        return titles;
    }

    public Page<SelectorsPenaltyResponse> findPenalties(
            Long generationId, PenaltyStatus status, boolean blacklistOnly, Pageable pageable) {
        Page<Selectors> page = selectorsRepository.searchWithPenalties(
                generationId, status, blacklistOnly, pageable);
        if (page.isEmpty()) {
            return page.map(selectors -> SelectorsPenaltyResponse.of(
                    selectors, List.of(), BLACKLIST_THRESHOLD));
        }

        List<Long> selectorsIds = page.getContent().stream().map(Selectors::getId).toList();
        List<PenaltyHistory> histories = generationId == null
                ? penaltyHistoryRepository.findAllBySelectorsIds(selectorsIds)
                : penaltyHistoryRepository.findAllBySelectorsIdsAndGenerationId(
                        selectorsIds, generationId);
        Map<Long, List<PenaltyHistory>> historiesBySelectorsId = histories.stream()
                .collect(Collectors.groupingBy(PenaltyHistory::getSelectorsId));

        return page.map(selectors -> SelectorsPenaltyResponse.of(
                selectors,
                historiesBySelectorsId.getOrDefault(selectors.getId(), List.of()),
                BLACKLIST_THRESHOLD));
    }

    private SelectorsSummary toSummary(Selectors selectors, String roleName,
                                       Map<Long, SelectorsSnsAccount> representatives,
                                       Map<String, String> youtubeTitles) {
        SelectorsSnsAccount account = representatives.get(selectors.getId());
        return new SelectorsSummary(
                selectors.getId(),
                selectors.getSelectorsCode(),
                selectors.getSelectorsNickname(),
                selectors.getSelectorsRoleId(),
                roleName,
                selectors.getCategory(),
                account == null || account.getSnsCode() == null
                        ? null : account.getSnsCode().name(),
                account == null ? null : account.getAccountId(),
                snsDisplayName(account, youtubeTitles),
                account == null ? null : account.getFollowerCount(),
                account == null ? null : account.getProfileImageUrl(),
                selectors.getCreatedAt()
        );
    }

    private Map<String, String> youtubeChannelTitles(
            Map<Long, SelectorsSnsAccount> representatives) {
        List<String> channelIds = representatives.values().stream()
                .filter(account -> account.getSnsCode() == SnsPlatform.YOUTUBE)
                .map(SelectorsSnsAccount::getAccountId)
                .toList();
        return channelIds.isEmpty()
                ? Map.of() : youtubeContentFetcher.fetchChannelTitles(channelIds);
    }

    private String snsDisplayName(
            SelectorsSnsAccount account, Map<String, String> youtubeTitles) {
        if (account == null || account.getAccountId() == null) {
            return null;
        }
        return account.getSnsCode() == SnsPlatform.YOUTUBE
                ? youtubeTitles.getOrDefault(account.getAccountId(), account.getAccountId())
                : account.getAccountId();
    }

    private Map<String, String> roleNames() {
        return selectorsRoleRepository.findAll().stream()
                .collect(Collectors.toMap(SelectorsRole::getId, SelectorsRole::getRoleName));
    }

    private Map<Long, SelectorsSnsAccount> representativeAccounts(List<Long> selectorsIds) {
        return selectorsSnsAccountRepository
                .findAllBySelectorsIdInAndDeletedFalse(selectorsIds).stream()
                .collect(Collectors.toMap(
                        SelectorsSnsAccount::getSelectorsId,
                        account -> account));
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
