package com.fuma.hiselectors.application.service;

import com.fuma.hiselectors.application.dto.AdminApplicationDetailResponse;
import com.fuma.hiselectors.application.dto.AdminApplicationDetailResponse.ContentFormatCount;
import com.fuma.hiselectors.application.dto.AdminApplicationDetailResponse.MetricAverage;
import com.fuma.hiselectors.application.dto.AdminApplicationDetailResponse.QuantitativeMetrics;
import com.fuma.hiselectors.application.dto.AdminApplicationDetailResponse.UploadCadence;
import com.fuma.hiselectors.application.dto.AdminApplicationSummaryResponse;
import com.fuma.hiselectors.application.dto.ApplicationMediaResponse;
import com.fuma.hiselectors.application.model.Application;
import com.fuma.hiselectors.application.model.ApplicationMedia;
import com.fuma.hiselectors.application.model.ApplicationStatus;
import com.fuma.hiselectors.application.model.SnsPlatform;
import com.fuma.hiselectors.application.repository.ApplicationMediaRepository;
import com.fuma.hiselectors.application.repository.ApplicationRepository;
import com.fuma.hiselectors.exception.BusinessException;
import com.fuma.hiselectors.exception.ErrorCode;
import com.fuma.hiselectors.generation.model.Generation;
import com.fuma.hiselectors.generation.repository.GenerationRepository;
import com.fuma.hiselectors.user.model.User;
import com.fuma.hiselectors.user.repository.UserRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ApplicationAdminService {

    private static final int ANALYSIS_WINDOW_DAYS = 90;
    private static final int DECIMAL_SCALE = 2;

    private final ApplicationRepository applicationRepository;
    private final ApplicationMediaRepository mediaRepository;
    private final UserRepository userRepository;
    private final GenerationRepository generationRepository;

    public Page<AdminApplicationSummaryResponse> search(
            String keyword,
            SnsPlatform snsCode,
            ApplicationStatus status,
            Long generationId,
            Boolean minimumCriteriaOnly,
            Pageable pageable) {
        Page<Application> applications = applicationRepository.searchAdmin(
                normalize(keyword), snsCode, status, generationId, minimumCriteriaOnly, pageable);
        List<Long> applicationIds = applications.stream().map(Application::getId).toList();
        Map<Long, User> users = byId(userRepository.findAllById(
                applications.stream().map(Application::getUserId).distinct().toList()), User::getId);
        Map<Long, Generation> generations = byId(generationRepository.findAllById(
                applications.stream().map(Application::getGenerationId).distinct().toList()),
                Generation::getId);
        Map<Long, List<ApplicationMedia>> mediaByApplication = applicationIds.isEmpty()
                ? Map.of()
                : mediaRepository
                        .findAllByApplicationIdInOrderByApplicationIdAscSequenceNoAsc(applicationIds)
                        .stream()
                        .collect(Collectors.groupingBy(ApplicationMedia::getApplicationId));

        return applications.map(application -> toSummary(
                application,
                requiredUser(users, application.getUserId()),
                requiredGeneration(generations, application.getGenerationId()),
                mediaByApplication.getOrDefault(application.getId(), List.of())));
    }

    public AdminApplicationDetailResponse findDetail(Long applicationId) {
        Application application = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.APPLICATION_USER_NOT_FOUND));
        User user = userRepository.findById(application.getUserId())
                .orElseThrow(() -> new BusinessException(ErrorCode.APPLICATION_USER_NOT_FOUND));
        Generation generation = generationRepository.findById(application.getGenerationId())
                .orElseThrow(() -> new BusinessException(ErrorCode.GENERATION_NOT_FOUND));
        List<ApplicationMedia> contents = mediaRepository
                .findAllByApplicationIdOrderBySequenceNoAsc(applicationId);
        List<ApplicationMedia> recentContents = recentContents(application, contents);

        return new AdminApplicationDetailResponse(
                application.getId(),
                application.getUserId(),
                user.getHiId(),
                user.getName(),
                user.getEmail(),
                user.getPhone(),
                generation.getId(),
                generation.getGenerationName(),
                application.getSnsCode(),
                application.getSnsAccountId(),
                application.getFollowerCount(),
                application.getStatus(),
                application.getMediaCollectionStatus(),
                application.getCreatedAt(),
                application.getMediaCollectedAt(),
                application.getUpdatedAt(),
                metrics(application, recentContents),
                contents.stream().map(ApplicationMediaResponse::from).toList());
    }

    private AdminApplicationSummaryResponse toSummary(
            Application application,
            User user,
            Generation generation,
            List<ApplicationMedia> contents) {
        List<ApplicationMedia> recentContents = recentContents(application, contents);
        Long recentCount = application.getMediaCollectedAt() == null
                ? null : (long) recentContents.size();
        return new AdminApplicationSummaryResponse(
                application.getId(),
                application.getUserId(),
                user.getHiId(),
                user.getName(),
                user.getEmail(),
                user.getPhone(),
                generation.getId(),
                generation.getGenerationName(),
                application.getSnsCode(),
                application.getSnsAccountId(),
                application.getFollowerCount(),
                application.getContentCount(),
                recentCount,
                engagementRate(recentContents, application.getFollowerCount()).value(),
                application.getStatus(),
                application.getMediaCollectionStatus(),
                application.getCreatedAt(),
                application.getMediaCollectedAt(),
                application.getUpdatedAt());
    }

    private QuantitativeMetrics metrics(
            Application application, List<ApplicationMedia> recentContents) {
        boolean collected = application.getMediaCollectedAt() != null;
        Long recentCount = collected ? (long) recentContents.size() : null;
        LocalDateTime lastPublishedAt = recentContents.stream()
                .map(ApplicationMedia::getPublishedAt)
                .max(LocalDateTime::compareTo)
                .orElse(null);

        return new QuantitativeMetrics(
                ANALYSIS_WINDOW_DAYS,
                application.getContentCount(),
                recentCount,
                lastPublishedAt,
                cadence(recentContents, collected),
                average(recentContents, ApplicationMedia::getViewCount),
                average(recentContents, ApplicationMedia::getLikeCount),
                average(recentContents, ApplicationMedia::getCommentCount),
                engagementRate(recentContents, application.getFollowerCount()),
                contentFormats(recentContents));
    }

    private List<ApplicationMedia> recentContents(
            Application application, List<ApplicationMedia> contents) {
        LocalDateTime collectedAt = application.getMediaCollectedAt();
        if (collectedAt == null) {
            return List.of();
        }
        LocalDateTime collectedAfter = collectedAt.minusDays(ANALYSIS_WINDOW_DAYS);
        return contents.stream()
                .filter(content -> content.getPublishedAt() != null)
                .filter(content -> !content.getPublishedAt().isBefore(collectedAfter))
                .filter(content -> !content.getPublishedAt().isAfter(collectedAt))
                .toList();
    }

    private UploadCadence cadence(List<ApplicationMedia> contents, boolean collected) {
        if (!collected) {
            return new UploadCadence(0, null, null, null);
        }
        long sampleCount = contents.size();
        BigDecimal daily = BigDecimal.valueOf(sampleCount)
                .divide(BigDecimal.valueOf(ANALYSIS_WINDOW_DAYS), DECIMAL_SCALE, RoundingMode.HALF_UP);
        BigDecimal weekly = BigDecimal.valueOf(sampleCount * 7L)
                .divide(BigDecimal.valueOf(ANALYSIS_WINDOW_DAYS), DECIMAL_SCALE, RoundingMode.HALF_UP);

        List<LocalDateTime> published = contents.stream()
                .map(ApplicationMedia::getPublishedAt)
                .sorted()
                .toList();
        Long maximumGapDays = null;
        for (int index = 1; index < published.size(); index++) {
            long gap = ChronoUnit.DAYS.between(
                    published.get(index - 1).toLocalDate(),
                    published.get(index).toLocalDate());
            maximumGapDays = maximumGapDays == null ? gap : Math.max(maximumGapDays, gap);
        }
        return new UploadCadence(sampleCount, daily, weekly, maximumGapDays);
    }

    private MetricAverage average(
            List<ApplicationMedia> contents, Function<ApplicationMedia, Long> metric) {
        List<BigDecimal> measured = contents.stream()
                .map(metric)
                .filter(value -> value != null)
                .map(BigDecimal::valueOf)
                .toList();
        return averageOf(measured);
    }

    private MetricAverage engagementRate(List<ApplicationMedia> contents, Long followerCount) {
        if (followerCount == null || followerCount <= 0) {
            return new MetricAverage(null, 0);
        }
        List<BigDecimal> measured = contents.stream()
                .filter(content -> content.getLikeCount() != null
                        && content.getCommentCount() != null)
                .map(content -> BigDecimal.valueOf(content.getLikeCount())
                        .add(BigDecimal.valueOf(content.getCommentCount()))
                        .multiply(BigDecimal.valueOf(100))
                        .divide(BigDecimal.valueOf(followerCount), 8, RoundingMode.HALF_UP))
                .toList();
        return averageOf(measured);
    }

    private MetricAverage averageOf(List<BigDecimal> values) {
        if (values.isEmpty()) {
            return new MetricAverage(null, 0);
        }
        BigDecimal sum = values.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        return new MetricAverage(
                sum.divide(BigDecimal.valueOf(values.size()), DECIMAL_SCALE, RoundingMode.HALF_UP),
                values.size());
    }

    private List<ContentFormatCount> contentFormats(List<ApplicationMedia> contents) {
        Map<String, Long> counts = contents.stream().collect(Collectors.groupingBy(
                content -> content.getContentType() == null
                        ? "UNKNOWN" : content.getContentType().name(),
                TreeMap::new,
                Collectors.counting()));
        return counts.entrySet().stream()
                .map(entry -> new ContentFormatCount(entry.getKey(), entry.getValue()))
                .toList();
    }

    private String normalize(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return null;
        }
        return keyword.trim();
    }

    private <T> Map<Long, T> byId(List<T> values, Function<T, Long> id) {
        Map<Long, T> result = new HashMap<>();
        values.forEach(value -> result.put(id.apply(value), value));
        return result;
    }

    private User requiredUser(Map<Long, User> users, Long userId) {
        User user = users.get(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.APPLICATION_USER_NOT_FOUND);
        }
        return user;
    }

    private Generation requiredGeneration(Map<Long, Generation> generations, Long generationId) {
        Generation generation = generations.get(generationId);
        if (generation == null) {
            throw new BusinessException(ErrorCode.GENERATION_NOT_FOUND);
        }
        return generation;
    }
}
