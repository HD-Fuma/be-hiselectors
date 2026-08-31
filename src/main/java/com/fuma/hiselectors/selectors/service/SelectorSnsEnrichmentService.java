package com.fuma.hiselectors.selectors.service;

import com.fuma.hiselectors.application.model.SnsPlatform;
import com.fuma.hiselectors.application.service.LocalAnalyzerClient;
import com.fuma.hiselectors.application.service.LocalAnalyzerClient.LocalAnalysis;
import com.fuma.hiselectors.content.client.ContentFetcher;
import com.fuma.hiselectors.content.client.dto.RawContent;
import com.fuma.hiselectors.exception.BusinessException;
import com.fuma.hiselectors.exception.ErrorCode;
import com.fuma.hiselectors.selectors.dto.SelectorSnsEnrichmentResponse;
import com.fuma.hiselectors.selectors.model.Selectors;
import com.fuma.hiselectors.selectors.model.SelectorsSnsAccount;
import com.fuma.hiselectors.selectors.repository.SelectorsRepository;
import com.fuma.hiselectors.selectors.repository.SelectorsSnsAccountRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 지원서 파이프라인 없이 셀렉터스 SNS 계정만으로 프로필 이미지와 대표 카테고리를 채운다.
 *
 * <p>프로필은 공개 프로필 API, 카테고리는 최근 콘텐츠 제목·설명·캡션을 로컬 분석 워커에 넘겨
 * 공식 코드 최빈값을 쓴다. STT/Gemini 리포트는 만들지 않는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SelectorSnsEnrichmentService {

    static final int COLLECTION_DAYS = 90;
    static final int CONTENT_LIMIT = 10;
    static final int TEXT_MAX = 4_000;
    static final Set<String> CATEGORY_CODES = Set.of(
            "BEAUTY", "FASHION", "FOOD", "LIVING_LIFE", "KIDS_FAMILY",
            "CULTURE_SERVICE", "SPORTS_LEISURE", "TRAVEL", "PET_LIFE");

    private final SelectorsRepository selectorsRepository;
    private final SelectorsSnsAccountRepository selectorsSnsAccountRepository;
    private final List<ContentFetcher> contentFetchers;
    private final LocalAnalyzerClient analyzer;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    public SelectorSnsEnrichmentResponse.Batch enrichMissing(boolean force, int limit) {
        int batchSize = Math.max(1, Math.min(limit, 50));
        List<Selectors> targets = selectorsRepository.findSnsEnrichmentTargets(
                force, PageRequest.of(0, batchSize));
        List<SelectorSnsEnrichmentResponse> results = new ArrayList<>();
        int failed = 0;
        for (Selectors selectors : targets) {
            try {
                results.add(enrich(selectors.getId(), force));
            } catch (RuntimeException e) {
                failed++;
                log.warn("셀렉터스 SNS 보강 실패: selectorsId={}", selectors.getId(), e);
                results.add(failedResult(selectors.getId(), e));
            }
        }
        return new SelectorSnsEnrichmentResponse.Batch(
                targets.size(),
                (int) results.stream().filter(SelectorSnsEnrichmentResponse::profileImageUpdated).count(),
                (int) results.stream().filter(SelectorSnsEnrichmentResponse::categoryUpdated).count(),
                failed,
                List.copyOf(results));
    }

    public SelectorSnsEnrichmentResponse enrich(Long selectorsId, boolean force) {
        Selectors selectors = selectorsRepository.findByIdAndDeletedFalse(selectorsId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SELECTOR_NOT_FOUND));
        SelectorsSnsAccount account = selectorsSnsAccountRepository
                .findBySelectorsIdAndDeletedFalse(selectorsId)
                .filter(value -> hasText(value.getAccountId()))
                .orElseThrow(() -> new BusinessException(ErrorCode.SELECTOR_SNS_ACCOUNT_NOT_FOUND));

        boolean needProfile = force || !hasText(account.getProfileImageUrl());
        boolean needCategory = force || !hasText(selectors.getCategory());
        if (!needProfile && !needCategory) {
            return new SelectorSnsEnrichmentResponse(
                    selectorsId,
                    account.getProfileImageUrl(),
                    false,
                    selectors.getCategory(),
                    false,
                    "이미 프로필 이미지가 있습니다.",
                    "이미 카테고리가 있습니다.");
        }

        ContentFetcher fetcher = findFetcher(account.getSnsCode());
        ProfileDraft profile = needProfile
                ? fetchProfile(fetcher, account)
                : ProfileDraft.skipped(account.getProfileImageUrl(), "이미 프로필 이미지가 있습니다.");
        CategoryDraft category = needCategory
                ? classify(fetcher, account)
                : CategoryDraft.skipped(selectors.getCategory(), "이미 카테고리가 있습니다.");

        persist(selectorsId, force, profile, category);

        if (category.error() != null) {
            throw category.error();
        }
        return new SelectorSnsEnrichmentResponse(
                selectorsId,
                firstNonBlank(profile.imageUrl(), account.getProfileImageUrl()),
                profile.updated(),
                firstNonBlank(category.code(), selectors.getCategory()),
                category.updated(),
                profile.skipReason(),
                category.skipReason());
    }

    private void persist(
            Long selectorsId,
            boolean force,
            ProfileDraft profile,
            CategoryDraft category) {
        transactionTemplate.executeWithoutResult(status -> {
            Selectors locked = selectorsRepository.findByIdForUpdate(selectorsId)
                    .filter(selectors -> !selectors.isDeleted())
                    .orElseThrow(() -> new BusinessException(ErrorCode.SELECTOR_NOT_FOUND));
            if (category.updated()) {
                locked.assignCategory(category.code());
            }
            selectorsSnsAccountRepository.findBySelectorsIdAndDeletedFalseForUpdate(selectorsId)
                    .ifPresent(account -> {
                        if (profile.updated()) {
                            account.applyPublicProfile(
                                    profile.imageUrl(), profile.followerCount(), force);
                        } else if (profile.followerCount() != null) {
                            account.applyPublicProfile(null, profile.followerCount(), false);
                        }
                    });
        });
    }

    private ProfileDraft fetchProfile(ContentFetcher fetcher, SelectorsSnsAccount account) {
        try {
            ContentFetcher.Profile profile = fetcher.fetchProfile(account.getAccountId());
            if (profile == null) {
                return ProfileDraft.skipped(
                        account.getProfileImageUrl(), "공개 프로필 이미지를 찾지 못했습니다.");
            }
            if (!hasText(profile.imageUrl())) {
                return new ProfileDraft(
                        account.getProfileImageUrl(),
                        profile.followerCount(),
                        false,
                        "공개 프로필 이미지를 찾지 못했습니다.",
                        null);
            }
            return new ProfileDraft(profile.imageUrl(), profile.followerCount(), true, null, null);
        } catch (RuntimeException e) {
            log.warn("셀렉터스 공개 프로필 조회 실패: selectorsId={}, platform={}, cause={}",
                    account.getSelectorsId(), account.getSnsCode(), e.getClass().getSimpleName());
            return ProfileDraft.skipped(
                    account.getProfileImageUrl(), "공개 프로필 조회에 실패했습니다.");
        }
    }

    private CategoryDraft classify(ContentFetcher fetcher, SelectorsSnsAccount account) {
        List<RawContent> contents;
        try {
            contents = fetchContents(fetcher, account);
        } catch (RuntimeException e) {
            log.warn("셀렉터스 콘텐츠 조회 실패: selectorsId={}, platform={}, cause={}",
                    account.getSelectorsId(), account.getSnsCode(), e.getClass().getSimpleName());
            return CategoryDraft.skipped(null, "최근 콘텐츠를 조회하지 못했습니다.");
        }
        List<String> texts = rankedContents(contents).stream()
                .map(RawContent::caption)
                .filter(this::hasText)
                .map(text -> clip(text, TEXT_MAX))
                .toList();
        if (texts.isEmpty()) {
            return CategoryDraft.skipped(null, "분류할 콘텐츠 텍스트가 없습니다.");
        }

        List<String> categories = new ArrayList<>();
        try {
            for (String text : texts) {
                String code = officialCategory(analyzer.analyze(text));
                if (code != null) {
                    categories.add(code);
                }
            }
        } catch (BusinessException e) {
            if (e.getErrorCode() == ErrorCode.ANALYZER_UNAVAILABLE) {
                return CategoryDraft.failed(e);
            }
            throw e;
        }
        String mode = mode(categories);
        if (mode == null) {
            return CategoryDraft.skipped(null, "공식 카테고리로 분류하지 못했습니다.");
        }
        return new CategoryDraft(mode, true, null, null);
    }

    private List<RawContent> fetchContents(ContentFetcher fetcher, SelectorsSnsAccount account) {
        LocalDateTime collectedAt = LocalDateTime.now(clock);
        LocalDateTime collectedAfter = account.getSnsCode() == SnsPlatform.INSTAGRAM
                ? LocalDateTime.MIN
                : collectedAt.minusDays(COLLECTION_DAYS);
        List<RawContent> contents = account.getSnsCode() == SnsPlatform.INSTAGRAM
                ? fetcher.fetchByAccount(account.getAccountId(), collectedAfter, CONTENT_LIMIT)
                : fetcher.fetchByAccount(account.getAccountId(), collectedAfter);
        List<RawContent> valid = contents.stream()
                .filter(Objects::nonNull)
                .filter(content -> content.snsCode() == account.getSnsCode())
                .filter(content -> content.createdAt() != null)
                .filter(content -> !content.createdAt().isBefore(collectedAfter))
                .filter(content -> !content.createdAt().isAfter(collectedAt))
                .filter(content -> hasText(content.caption()))
                .toList();
        return fetcher.addStatistics(valid);
    }

    private List<RawContent> rankedContents(List<RawContent> contents) {
        Map<String, RawContent> latestById = contents.stream()
                .filter(content -> hasText(content.snsContentId()))
                .sorted(Comparator.comparing(RawContent::createdAt).reversed())
                .collect(Collectors.toMap(
                        RawContent::snsContentId,
                        content -> content,
                        (first, ignored) -> first,
                        LinkedHashMap::new));
        return latestById.values().stream()
                .sorted(Comparator.comparing(
                                RawContent::viewCount, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(RawContent::createdAt, Comparator.reverseOrder()))
                .limit(CONTENT_LIMIT)
                .toList();
    }

    private String officialCategory(LocalAnalysis local) {
        if (local == null || local.category() == null || local.category().uncertain()) {
            return null;
        }
        String normalized = local.categoryLabel() == null
                ? null
                : local.categoryLabel().trim().toUpperCase(Locale.ROOT);
        return normalized != null && CATEGORY_CODES.contains(normalized) ? normalized : null;
    }

    private String mode(List<String> values) {
        return values.stream()
                .collect(Collectors.groupingBy(value -> value, Collectors.counting()))
                .entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(null);
    }

    private ContentFetcher findFetcher(SnsPlatform platform) {
        return contentFetchers.stream()
                .filter(fetcher -> fetcher.supports() == platform)
                .findFirst()
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.INVALID_INPUT, "지원하지 않는 SNS 플랫폼입니다."));
    }

    private SelectorSnsEnrichmentResponse failedResult(Long selectorsId, RuntimeException error) {
        String reason = error instanceof BusinessException business
                ? business.getErrorCode().getMessage()
                : "셀렉터스 SNS 보강에 실패했습니다.";
        return new SelectorSnsEnrichmentResponse(
                selectorsId, null, false, null, false, reason, reason);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String firstNonBlank(String primary, String fallback) {
        return hasText(primary) ? primary : fallback;
    }

    private String clip(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max);
    }

    private record ProfileDraft(
            String imageUrl,
            Long followerCount,
            boolean updated,
            String skipReason,
            RuntimeException error) {

        static ProfileDraft skipped(String imageUrl, String reason) {
            return new ProfileDraft(imageUrl, null, false, reason, null);
        }
    }

    private record CategoryDraft(
            String code,
            boolean updated,
            String skipReason,
            BusinessException error) {

        static CategoryDraft skipped(String code, String reason) {
            return new CategoryDraft(code, false, reason, null);
        }

        static CategoryDraft failed(BusinessException error) {
            return new CategoryDraft(null, false, error.getErrorCode().getMessage(), error);
        }
    }
}
