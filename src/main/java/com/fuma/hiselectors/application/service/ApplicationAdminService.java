package com.fuma.hiselectors.application.service;

import com.fuma.hiselectors.application.dto.AdminAiReportResponse;
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
import com.fuma.hiselectors.application.model.ApplicationReport;
import com.fuma.hiselectors.application.model.SnsPlatform;
import com.fuma.hiselectors.application.repository.ApplicationMediaRepository;
import com.fuma.hiselectors.application.repository.ApplicationReportRepository;
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
import java.util.LinkedHashMap;
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
    private final ApplicationReportRepository reportRepository;
    private final UserRepository userRepository;
    private final GenerationRepository generationRepository;

    // 초기화된 final 이라 @RequiredArgsConstructor 생성자엔 안 들어감(주입 아님).
    private final tools.jackson.databind.ObjectMapper objectMapper =
            new tools.jackson.databind.ObjectMapper();

    /** 지원자 상세의 AI 리포트. 없으면 404(아직 미분석). */
    public AdminAiReportResponse findAiReport(Long applicationId) {
        ApplicationReport r = reportRepository
                .findFirstByApplicationIdOrderByCreatedAtDesc(applicationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.REPORT_NOT_FOUND));
        return new AdminAiReportResponse(
                r.getApplicationId(),
                decodeSummary(r.getSummary()),
                r.getCategory(),
                splitCsv(r.getKeywords()),
                r.getContentStyle(),
                r.getTone(),
                r.getStrength(),
                r.getWarning(),
                r.getBrandHistory(),
                r.getRepresentativeContentUrl(),
                r.getRepresentativeContentType(),
                r.getRepresentativeViewCount(),
                r.getRepresentativeCategory(),
                splitCsv(r.getRepresentativeKeywords()),
                r.getStatus(),
                r.getCreatedAt());
    }

    /** summary 는 json 컬럼(문자열 리터럴)로 저장됨 → 사람이 읽는 평문으로 디코드. 실패/빈값이면 원본/ null. */
    private String decodeSummary(String summary) {
        if (summary == null || summary.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(summary, String.class);
        } catch (RuntimeException e) {
            return summary;
        }
    }

    /** 콤마문자열 → 트리밍된 리스트. 없으면 빈 리스트. */
    private List<String> splitCsv(String csv) {
        if (csv == null || csv.isBlank()) {
            return List.of();
        }
        return java.util.Arrays.stream(csv.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }

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
                        .findAllByApplicationIdInOrderByApplicationIdAscSequenceNoAscMediaSequenceNoAsc(
                                applicationIds)
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
                .findAllByApplicationIdOrderBySequenceNoAscMediaSequenceNoAsc(applicationId);
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
                application.getProfileUrl(),
                application.getFollowerCount(),
                application.getStatus(),
                application.getMediaCollectionStatus(),
                application.getAnalysisStatus(),
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
                application.getProfileUrl(),
                application.getFollowerCount(),
                application.getContentCount(),
                recentCount,
                application.getEngagementRate(),
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
                engagementRate(application, recentContents),
                contentFormats(recentContents));
    }

    private List<ApplicationMedia> recentContents(
            Application application, List<ApplicationMedia> contents) {
        LocalDateTime collectedAt = application.getMediaCollectedAt();
        if (collectedAt == null) {
            return List.of();
        }
        LocalDateTime collectedAfter = collectedAt.minusDays(ANALYSIS_WINDOW_DAYS);
        return List.copyOf(contents.stream()
                .filter(content -> content.getPublishedAt() != null)
                .filter(content -> !content.getPublishedAt().isBefore(collectedAfter))
                .filter(content -> !content.getPublishedAt().isAfter(collectedAt))
                .collect(Collectors.toMap(
                        ApplicationMedia::getSnsContentId,
                        Function.identity(),
                        (first, ignored) -> first,
                        LinkedHashMap::new))
                .values());
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

    private MetricAverage engagementRate(
            Application application, List<ApplicationMedia> contents) {
        long sampleCount = contents.stream()
                .filter(content -> content.getViewCount() != null && content.getViewCount() > 0)
                .filter(content -> content.getLikeCount() != null && content.getLikeCount() >= 0)
                .filter(content -> content.getCommentCount() != null
                        && content.getCommentCount() >= 0)
                .count();
        return new MetricAverage(application.getEngagementRate(), sampleCount);
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
                content -> contentType(content.getContentType()),
                TreeMap::new,
                Collectors.counting()));
        return counts.entrySet().stream()
                .map(entry -> new ContentFormatCount(entry.getKey(), entry.getValue()))
                .toList();
    }

    private String contentType(com.fuma.hiselectors.content.model.ContentType type) {
        if (type == null) {
            return "UNKNOWN";
        }
        return switch (type) {
            case SHORT_FORM -> "REELS";
            case FEED -> "POST";
            default -> type.name();
        };
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
