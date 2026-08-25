package com.fuma.hiselectors.stt;

import com.fuma.hiselectors.application.model.ApplicationContentAnalysis;
import com.fuma.hiselectors.application.model.ApplicationMedia;
import com.fuma.hiselectors.application.model.ApplicationReport;
import com.fuma.hiselectors.application.repository.ApplicationContentAnalysisRepository;
import com.fuma.hiselectors.application.repository.ApplicationMediaRepository;
import com.fuma.hiselectors.application.repository.ApplicationReportRepository;
import com.fuma.hiselectors.application.repository.ApplicationRepository;
import com.fuma.hiselectors.application.service.LocalAnalyzerClient;
import com.fuma.hiselectors.selectors.repository.SelectorsRepository;
import com.fuma.hiselectors.application.service.ReportStatus;
import com.fuma.hiselectors.creator.discovery.MetaGraphApiClient;
import com.fuma.hiselectors.exception.BusinessException;
import com.fuma.hiselectors.exception.ErrorCode;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

/**
 * 지원자 종합 평가(A안 + DB 백업). 콘텐츠별 분석(비쌈)은 content_key 로 멱등 저장 →
 * 크래시·실패 후 재개 시 done 항목은 워커 재호출 없이 skip. 평가(쌈)는 저장분을 합쳐 Gemini 1회.
 *
 * <p><b>커넥션 주의:</b> 워커·Gemini 같은 장시간 외부 호출은 트랜잭션 <em>밖</em>에서 한다.
 * 여기에 @Transactional 을 걸면 외부 호출 수십 초 동안 DB 커넥션을 붙잡아 풀이 고갈된다.
 * 각 repository 호출은 그 자체로 짧은 트랜잭션이라, 사이의 외부 호출은 커넥션을 잡지 않는다.
 */
@Service
@RequiredArgsConstructor
public class CreatorEvaluationService {

    private static final int TEXT_MAX = 500;
    private static final int STYLE_MAX = 19;
    private static final int STT_INPUT_MAX = 1_000;
    private static final int OCR_INPUT_MAX = 500;
    private static final int POST_TEXT_INPUT_MAX = 500;
    private static final int REPORT_INPUT_MAX = 10_000;
    private static final Set<String> CATEGORY_CODES = Set.of(
            "BEAUTY", "FASHION", "FOOD", "LIVING_LIFE", "KIDS_FAMILY",
            "CULTURE_SERVICE", "SPORTS_LEISURE", "TRAVEL", "PET_LIFE");

    // summary(json 컬럼)용 인코더. 초기화된 final 이라 @RequiredArgsConstructor 생성자엔 안 들어감.
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final InstagramSttClient instagramClient;
    private final YoutubeSttClient youtubeClient;
    private final GeminiEvalClient evalClient;
    private final ApplicationContentAnalysisRepository repository;
    private final ApplicationMediaRepository mediaRepository;
    private final ApplicationReportRepository reportRepository;
    private final ApplicationRepository applicationRepository;
    private final SelectorsRepository selectorsRepository;
    private final MetaGraphApiClient metaGraphApiClient;
    private final LocalAnalyzerClient analyzer;
    private final TransactionTemplate transactionTemplate;

    /** 콘텐츠 1건 분석 후 적재. content_key 가 이미 있으면 재분석하지 않고 저장분을 돌려준다(멱등). */
    public InstagramAnalysisResult addContent(Long applicantId, ContentAddRequest req) {
        // 1) 짧은 조회. 이미 있으면 워커 호출도 저장도 없이 반환.
        Optional<ApplicationContentAnalysis> existing = repository.findByContentKey(req.contentKey());
        if (existing.isPresent()) {
            return existing.get().toResult();
        }

        // 2) 오래 걸리는 워커 호출 — 트랜잭션 밖(커넥션 미보유). media_url 만료면 1회 재취득 후 재시도.
        InstagramAnalysisResult result = analyzeWithRefresh(req);

        // stt·ocr 둘 다 비면 분석할 내용이 없음 — 저장하지 않아 취합·대표 후보에서 제외.
        if (noContent(result.stt(), result.ocr())) {
            return result;
        }

        // 3) 짧은 저장. 동시요청이 먼저 같은 content_key 를 저장했으면 중복은 성공으로 간주(멱등).
        try {
            repository.save(ApplicationContentAnalysis.from(applicantId, req.contentKey(), result));
        } catch (DataIntegrityViolationException duplicate) {
            // 다른 요청이 선점 저장 — 재분석 결과는 버리고 성공 취급.
        }
        return result;
    }

    /**
     * 유튜브 콘텐츠 1건을 전사해 적재한다. content_key(=videoId) 멱등이라 재시도 시 done 은 skip.
     * 인스타와 달리 media_url(CDN)이 없고, Gemini 가 watch URL 을 직접 읽어 전사한다.
     */
    public void addYoutubeContent(Long applicantId, String videoId) {
        if (repository.findByContentKey(videoId).isPresent()) {
            return;
        }
        SttResult result = youtubeClient.transcribe(videoId);
        // stt·ocr 둘 다 비면 분석할 내용이 없음 — 저장하지 않아 취합·대표 후보에서 제외.
        if (noContent(result.stt(), result.ocr())) {
            return;
        }
        LocalAnalyzerClient.LocalAnalysis local = analyzeLocally(result.stt(), result.ocr());
        List<String> kw = local.keywordsOrEmpty();
        try {
            repository.save(ApplicationContentAnalysis.builder()
                    .applicantId(applicantId)
                    .contentKey(videoId)
                    .source("youtube")
                    .stt(result.stt())
                    .ocr(result.ocr())
                    .category(blankToNull(local.categoryLabel()))
                    .keywords(kw.isEmpty() ? null : String.join(",", kw))
                    .hateSuspected(false)
                    .build());
        } catch (DataIntegrityViolationException duplicate) {
            // 다른 요청이 선점 저장 — 멱등 성공 취급.
        }
    }

    /**
     * 전사 텍스트로 로컬 엔진 category/keywords 산출(인스타 워커 Analysis 와 동급 신호).
     * 워커 장애 시 비싼 전사는 보존하고 분석만 비운다(빈 결과) — 유튜브 재전사는 비싸고 봇차단 위험.
     */
    private LocalAnalyzerClient.LocalAnalysis analyzeLocally(String stt, String ocr) {
        String text = (safe(stt) + " " + safe(ocr)).strip();
        if (text.isEmpty()) {
            return LocalAnalyzerClient.LocalAnalysis.empty();
        }
        try {
            return analyzer.analyze(text);
        } catch (BusinessException e) {
            return LocalAnalyzerClient.LocalAnalysis.empty();
        }
    }

    private String blankToNull(String v) {
        return v == null || v.isBlank() ? null : v;
    }

    /**
     * 워커 호출. media_url 만료(MEDIA_URL_EXPIRED)면 content_key(=media id)로 Graph API에서
     * fresh media_url 재취득 후 딱 1회 재시도한다. 그래도 만료/실패면 예외 그대로.
     */
    private InstagramAnalysisResult analyzeWithRefresh(ContentAddRequest req) {
        try {
            return instagramClient.analyze(req.mediaUrl(), req.thumbnailUrl());
        } catch (BusinessException e) {
            if (e.getErrorCode() != ErrorCode.MEDIA_URL_EXPIRED) {
                throw e;
            }
            MetaGraphApiClient.MediaUrls fresh = metaGraphApiClient.fetchMediaUrls(req.contentKey());
            return instagramClient.analyze(fresh.mediaUrl(), fresh.thumbnailUrl());
        }
    }

    /**
     * 지원자의 저장된 콘텐츠를 취합해 application_report 1건으로 저장한다.
     * category·keywords 는 로컬 분석의 결정적 취합(최빈·병합), 강점·스타일·톤 등 insight 는 Gemini 1회.
     * 평가 후 콘텐츠(application_content_analysis)는 파기(무저장).
     */
    public ApplicationReport evaluate(Long applicationId) {
        ApplicationReport report = buildReport(applicationId);   // Gemini 등 외부호출 = 트랜잭션 밖
        return transactionTemplate.execute(status -> persistReport(applicationId, report));
    }

    /**
     * 저장된 콘텐츠를 읽어 취합 리포트를 <b>만들기만</b> 한다(미저장). Gemini 호출 포함이라 트랜잭션 밖.
     * 스케줄러는 이걸로 리포트를 만든 뒤, 저장·상태갱신을 한 트랜잭션으로 묶는다({@link #persistReport}).
     */
    public ApplicationReport buildReport(Long applicationId) {
        List<ApplicationContentAnalysis> rows = repository.findByApplicantId(applicationId);
        List<ApplicationMedia> media =
                mediaRepository.findAllByApplicationIdOrderBySequenceNoAscMediaSequenceNoAsc(applicationId);
        // 분석 입력 = 콘텐츠별 전사·자막(stt/ocr) + 게시물 텍스트(인스타 caption / 유튜브 title·description).
        // 텍스트만 있고 전사·OCR 안 걸린 게시물도 이제 리포트에 반영된다.
        String merged = Stream.concat(
                        rows.stream().map(c -> (safe(clip(c.getStt(), STT_INPUT_MAX)) + " "
                                + safe(clip(c.getOcr(), OCR_INPUT_MAX))).strip()),
                        media.stream().map(m -> clip((safe(m.getCaption()) + " "
                                + safe(m.getTitle()) + " " + safe(m.getDescription())).strip(),
                                POST_TEXT_INPUT_MAX)))
                .filter(Objects::nonNull)
                .filter(s -> !s.isEmpty())
                .distinct()   // 캐러셀은 per-media 행이라 같은 caption 이 반복됨 → 중복 텍스트 제거
                .collect(Collectors.joining("\n\n"));
        if (merged.isEmpty()) {
            throw new BusinessException(ErrorCode.NO_CONTENT_TO_EVALUATE);
        }

        ApplicantInsight insight = evalClient.insight(clip(merged, REPORT_INPUT_MAX));
        String localCategory = mode(rows, ApplicationContentAnalysis::getCategory);
        String localKeywords = union(rows, ApplicationContentAnalysis::getKeywords);
        ApplicationReport.ApplicationReportBuilder builder = ApplicationReport.builder()
                .applicationId(applicationId)
                .summary(toJson(insight.summary()))
                .category(firstNonBlank(localCategory, validCategory(insight.category())))
                .keywords(clip(firstNonBlank(localKeywords, join(insight.keywords())), TEXT_MAX))
                .contentStyle(clip(insight.contentStyle(), STYLE_MAX))
                .tone(clip(insight.tone(), TEXT_MAX))
                .strength(clip(join(insight.strengths()), TEXT_MAX))
                .cautions(clip(join(insight.cautions()), TEXT_MAX))
                .risks(clip(join(risksWithHate(insight.risks(), insight.hateConfirmed())), TEXT_MAX))
                .brandHistory(clip(join(insight.collabBrands()), TEXT_MAX))
                .status(ReportStatus.AI_COMPLETED.name());
        applyRepresentative(media, rows, builder);   // buildReport 에서 이미 로드한 media 재사용
        return builder.build();
    }

    /**
     * 대표 콘텐츠 = 수집된 미디어 중 조회수 최고(콘텐츠 분석 성공 여부와 무관).
     * STT/OCR·Gemini 콘텐츠 분석이 실패·스킵돼 application_content_analysis 가 비어 있어도
     * 대표 콘텐츠는 항상 뽑히도록 media 목록에서 직접 고른다. 카테고리·키워드는 마침 그
     * 콘텐츠가 분석에 성공했으면 덧붙이고, 아니면 비워둔다(대표 콘텐츠 노출 자체는 막지 않음).
     */
    private void applyRepresentative(List<ApplicationMedia> mediaList, List<ApplicationContentAnalysis> rows,
                                     ApplicationReport.ApplicationReportBuilder builder) {
        ApplicationMedia media = mediaList.stream()
                .filter(m -> m.getSnsContentId() != null && m.getContentUrl() != null)
                .max(Comparator.comparing(ApplicationMedia::getViewCount, Comparator.nullsFirst(Comparator.naturalOrder())))
                .orElse(null);
        if (media == null) {
            return;
        }
        Map<String, ApplicationContentAnalysis> analysisByKey = rows.stream()
                .collect(Collectors.toMap(ApplicationContentAnalysis::getContentKey, Function.identity(), (a, b) -> a));
        ApplicationContentAnalysis analysis = analysisByKey.get(media.getSnsContentId());
        builder.representativeContentUrl(media.getContentUrl())
                .representativeContentType(media.getContentType() == null ? null : media.getContentType().name())
                .representativeViewCount(media.getViewCount())
                .representativeCategory(analysis == null ? null : analysis.getCategory())
                .representativeKeywords(analysis == null ? null : clip(analysis.getKeywords(), TEXT_MAX));
    }

    /** 기존 리포트 교체 저장 + 콘텐츠 파기. 반드시 트랜잭션 안에서 호출(외부호출 없음). */
    public ApplicationReport persistReport(Long applicationId, ApplicationReport report) {
        reportRepository.deleteByApplicationId(applicationId);
        ApplicationReport saved = reportRepository.save(report);
        repository.deleteByApplicantId(applicationId);
        backfillSelectorCategory(applicationId, saved.getCategory());
        return saved;
    }

    /** 이미 승인된 셀렉터스면 분석 카테고리를 소급 지정(승인이 분석보다 먼저였거나 재평가로 바뀐 경우). */
    private void backfillSelectorCategory(Long applicationId, String category) {
        if (category == null || category.isBlank()) {
            return;
        }
        applicationRepository.findById(applicationId)
                .flatMap(a -> selectorsRepository.findByUserId(a.getUserId()))
                .ifPresent(selectors -> selectors.assignCategory(category));
    }

    /** 콘텐츠별 값 최빈(non-blank). 없으면 null. */
    private String mode(List<ApplicationContentAnalysis> rows,
                        Function<ApplicationContentAnalysis, String> getter) {
        return rows.stream()
                .map(getter)
                .filter(v -> v != null && !v.isBlank())
                .collect(Collectors.groupingBy(v -> v, Collectors.counting()))
                .entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(null);
    }

    /** 콤마로 이어진 값들을 펼쳐 순서보존 중복제거 후 다시 잇는다. 없으면 null. */
    private String union(List<ApplicationContentAnalysis> rows,
                         Function<ApplicationContentAnalysis, String> getter) {
        Set<String> merged = new LinkedHashSet<>();
        for (ApplicationContentAnalysis row : rows) {
            String value = getter.apply(row);
            if (value == null || value.isBlank()) {
                continue;
            }
            for (String part : value.split(",")) {
                String trimmed = part.trim();
                if (!trimmed.isEmpty()) {
                    merged.add(trimmed);
                }
            }
        }
        return merged.isEmpty() ? null : String.join(", ", merged);
    }

    /** 위험요소 = risks taxonomy + (욕설 확정 시 표식). 유의점(cautions)과는 별도 저장. */
    private List<String> risksWithHate(List<String> risks, boolean hateConfirmed) {
        List<String> merged = new ArrayList<>();
        if (risks != null) {
            merged.addAll(risks);
        }
        if (hateConfirmed) {
            merged.add("욕설/혐오");
        }
        return merged;
    }

    private String join(List<String> values) {
        return values == null ? "" : String.join(", ", values);
    }

    /** 요약을 json 컬럼용 유효 JSON 문자열로(따옴표 포함). 없으면 null. */
    private String toJson(String summary) {
        return summary == null || summary.isBlank() ? null : objectMapper.writeValueAsString(summary);
    }

    private String clip(String value, int max) {
        if (value == null || value.isEmpty()) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }

    private String safe(String s) {
        return s == null ? "" : s;
    }

    private String firstNonBlank(String primary, String fallback) {
        return primary == null || primary.isBlank() ? blankToNull(fallback) : primary;
    }

    private String validCategory(String category) {
        if (category == null) {
            return null;
        }
        String normalized = category.trim().toUpperCase(Locale.ROOT);
        return CATEGORY_CODES.contains(normalized) ? normalized : null;
    }

    /** 전사·자막 둘 다 비었으면 분석할 내용 없음. */
    private boolean noContent(String stt, String ocr) {
        return (stt == null || stt.isBlank()) && (ocr == null || ocr.isBlank());
    }
}
