package com.fuma.hiselectors.inspection.service;

import com.fuma.hiselectors.application.model.SnsPlatform;
import com.fuma.hiselectors.content.model.Content;
import com.fuma.hiselectors.content.model.ContentReportAnalysis;
import com.fuma.hiselectors.content.model.MediaType;
import com.fuma.hiselectors.inspection.extraction.model.ContentMediaExtractionResult;
import com.fuma.hiselectors.inspection.extraction.model.ContentMediaExtractionResult.OcrExtraction;
import com.fuma.hiselectors.inspection.extraction.model.ContentMediaExtractionResult.OcrSegment;
import com.fuma.hiselectors.inspection.extraction.model.ContentMediaExtractionResult.SttExtraction;
import com.fuma.hiselectors.inspection.extraction.model.ContentMediaExtractionResult.SttSegment;
import com.fuma.hiselectors.inspection.extraction.model.CoordinateSpace;
import com.fuma.hiselectors.inspection.extraction.model.NormalizedBoundingBox;
import com.fuma.hiselectors.inspection.model.DetectedViolation;
import com.fuma.hiselectors.inspection.model.EvidenceCoordinateSpace;
import com.fuma.hiselectors.inspection.model.EvidenceLocation;
import com.fuma.hiselectors.inspection.model.EvidenceSource;
import com.fuma.hiselectors.inspection.model.EvidenceTargetKind;
import com.fuma.hiselectors.inspection.model.ViolationEvidence;
import com.fuma.hiselectors.inspection.model.ViolationTypeCode;
import com.fuma.hiselectors.selectors.repository.SelectorsSnsAccountRepository;
import java.util.List;
import org.springframework.stereotype.Component;

/** 시연 계정의 외부 AI 호출을 고정 검수 결과로 대체한다. */
@Component
public class DemoYoutubeInspectionProvider {

    static final String ACCOUNT_ID = "UCD2RQE52TloxzZxZ2fyq8HQ";
    private static final long DELAY_MS = 5_000L;

    private final SelectorsSnsAccountRepository accountRepository;

    public DemoYoutubeInspectionProvider(SelectorsSnsAccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    public boolean supports(Content content) {
        if (content.getSnsCode() != SnsPlatform.YOUTUBE) {
            return false;
        }
        return accountRepository.findBySelectorsIdAndDeletedFalse(content.getSelectorsId())
                .filter(account -> account.getSnsCode() == SnsPlatform.YOUTUBE)
                .map(account -> ACCOUNT_ID.equals(account.getAccountId()))
                .orElse(false);
    }

    public void awaitResult() {
        try {
            Thread.sleep(DELAY_MS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("시연용 콘텐츠 검수 대기가 중단되었습니다.", exception);
        }
    }

    public ContentMediaExtractionResult extraction() {
        return new ContentMediaExtractionResult(
                ContentMediaExtractionResult.CURRENT_SCHEMA_VERSION,
                new SttExtraction("ko", List.of(
                        stt("stt-001", 0, 4_000, "안녕하세요 현대백화점입니다. 추석 선물 세트 소개해 드릴게요."),
                        stt("stt-002", 4_000, 10_000, "이 망고 정말 거대하고 달달한데요. 당도 100%의"),
                        stt("stt-003", 10_000, 14_000, "이것만 먹어도 살이 쪽쪽 빠져요. 다들 저만 믿고 구매하세요."),
                        stt("stt-004", 15_000, 19_000, "품절 직전이라는데요! 지금 당장 구매 GO!!!!"))),
                new OcrExtraction(List.of(
                        ocr("ocr-001", 0, 2_000, "안녕하세요 현대백화점입니다", .28, .19, .44, .08),
                        ocr("ocr-002", 2_000, 4_500, "추석 선물 세트 소개해드릴게요", .31, .19, .38, .08),
                        ocr("ocr-003", 4_500, 8_500, "이 망고 정말 거대하고 달달한데요", .29, .19, .42, .08),
                        ocr("ocr-004", 8_500, 10_000, "당도 100%의", .38, .19, .24, .04),
                        ocr("ocr-005", 10_000, 12_000, "이것만 먹어도 살이 쪽쪽 빠져요", .26, .19, .48, .04),
                        ocr("ocr-006", 12_000, 14_500, "다들 저만 믿고 구매하세요", .30, .19, .40, .04),
                        ocr("ocr-007", 14_500, 15_500, "!!!", .42, .14, .16, .06),
                        ocr("ocr-008", 15_500, 16_500, "품절 직전이라는데요!", .35, .19, .30, .04),
                        ocr("ocr-009", 16_500, 19_000, "지금 당장 구매 GO!!!!", .33, .19, .34, .04))),
                report());
    }

    public List<DetectedViolation> violations(Long contentMediaId) {
        String reason = "망고를 먹기만 해도 살이 빠진다는 표현은 객관적인 의학적/영양학적 근거가 없는 "
                + "검증되지 않은 효능에 해당하며, '당도 100%' 역시 현실적으로 불가능한 수치로 소비자에게 "
                + "오해를 불러일으킬 수 있는 최상급의 단정적 표현입니다.";
        return List.of(new DetectedViolation(
                ViolationTypeCode.FALSE_EXAGGERATED_CLAIM,
                new ViolationEvidence(reason, 1.0, List.of(
                        location(contentMediaId, EvidenceTargetKind.STT_SEGMENT,
                                "stt-002", "당도 100%의"),
                        location(contentMediaId, EvidenceTargetKind.STT_SEGMENT,
                                "stt-003", "이것만 먹어도 살이 쪽쪽 빠져요"),
                        location(contentMediaId, EvidenceTargetKind.OCR_SEGMENT,
                                "ocr-004", "당도 100%의"),
                        location(contentMediaId, EvidenceTargetKind.OCR_SEGMENT,
                                "ocr-005", "이것만 먹어도 살이 쪽쪽 빠져요")), EvidenceSource.AI)));
    }

    private ContentReportAnalysis report() {
        return new ContentReportAnalysis(
                new ContentReportAnalysis.Overview(
                        "현대백화점의 추석 선물 세트인 망고를 소개하고 판매를 독려하는 숏폼 형식의 광고 영상입니다.",
                        "추석 선물 세트(망고)의 특징을 강조하여 시청자의 구매를 유도하기 위한 홍보 영상입니다.",
                        "현대백화점 소속임을 밝히며 인사를 나눈 뒤, 판매 중인 망고의 크기와 맛을 설명합니다. 이후 체중 감량 효과와 당도를 과장하여 언급하며 품절 임박을 알리고 빠른 구매를 촉구하며 마무리됩니다.",
                        "밝고 활기찬 분위기로 상품을 소개하고 있으나, 식품의 효능과 성분에 대해 검증되지 않은 자극적이고 과장된 표현을 사용하여 소비자에게 잘못된 정보를 전달할 위험이 큽니다."),
                new ContentReportAnalysis.Insight(
                        "출연자가 전면에 등장하여 직접 말을 걸고 자막을 활용해 강조점을 전달하는 전형적인 홈쇼핑/광고 스타일입니다.",
                        "자신감 넘치고 호객을 유도하는 높은 텐션의 어조",
                        List.of("품절 임박 강조와 명확한 구매 촉구(Call to Action)를 통해 구매 의욕을 자극함"),
                        List.of("다이어트 효과 등 객관적 근거가 없는 허위 사실을 주장하여 광고 신뢰도를 저해함"),
                        List.of("식품위생법 및 표시광고법 위반 소지가 있는 과대광고 표현 포함"),
                        false,
                        List.of("현대백화점")));
    }

    private SttSegment stt(String id, long start, long end, String text) {
        return new SttSegment(id, start, end, text);
    }

    private OcrSegment ocr(
            String id, long start, long end, String text,
            double x, double y, double width, double height) {
        return new OcrSegment(id, start, end, text, CoordinateSpace.NORMALIZED,
                new NormalizedBoundingBox(x, y, width, height));
    }

    private EvidenceLocation location(
            Long mediaId, EvidenceTargetKind kind, String segmentId, String excerpt) {
        return new EvidenceLocation(
                mediaId, MediaType.VIDEO, kind,
                EvidenceCoordinateSpace.CONTENT_MEDIA_SEGMENT,
                segmentId, null, null, excerpt);
    }
}
