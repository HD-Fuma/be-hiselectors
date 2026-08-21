package com.fuma.hiselectors.stt;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "STT (테스트)", description = "SNS 콘텐츠 음성·자막 추출 (Gemini). 저장하지 않음")
@Profile("local")
@RestController
@RequestMapping("/api/stt")
@RequiredArgsConstructor
public class SttTestController {

    private final SttService sttService;
    private final CreatorEvaluationService evaluationService;

    @Operation(summary = "콘텐츠 음성·자막 추출",
            description = "유튜브 videoId 로 Gemini 를 호출해 음성(오디오)과 자막(화면 텍스트)을 "
                    + "각각 추출한다. 결과는 저장하지 않는다. 유튜브만 지원한다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "추출 성공"),
            @ApiResponse(responseCode = "400", description = "STT 미지원 SNS", content = @Content),
            @ApiResponse(responseCode = "500", description = "Gemini API 키 미설정", content = @Content),
            @ApiResponse(responseCode = "502", description = "Gemini 호출 실패", content = @Content)
    })
    @GetMapping("/transcribe")
    public ResponseEntity<SttResult> transcribe(
            @Parameter(description = "SNS 코드", example = "YOUTUBE")
            @RequestParam(defaultValue = "YOUTUBE") String snsCode,
            @Parameter(description = "콘텐츠 ID (유튜브 videoId)", example = "uqVIyVablhQ",
                    required = true)
            @RequestParam String snsContentId) {
        return ResponseEntity.ok(sttService.transcribe(snsCode, snsContentId));
    }

    @Operation(summary = "인스타 릴스 분석",
            description = "릴스 URL 을 파이썬 워커에 넘겨 취득·STT·OCR·정성분석(키워드·카테고리·"
                    + "욕설혐오)까지 수행한다. 저장하지 않는다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "분석 성공"),
            @ApiResponse(responseCode = "502", description = "STT 워커 호출 실패", content = @Content)
    })
    @GetMapping("/instagram")
    public ResponseEntity<InstagramAnalysisResult> analyzeInstagram(
            @Parameter(description = "릴스 permalink", required = true,
                    example = "https://www.instagram.com/reels/DbXos7-kgtj/")
            @RequestParam String reelUrl) {
        return ResponseEntity.ok(sttService.analyzeInstagramReel(reelUrl));
    }

    @Operation(summary = "지원자 콘텐츠 추가",
            description = "릴스 1개를 분석해 지원자 캐시에 적재한다(여러 번 호출로 N개 축적). "
                    + "콘텐츠별 결과를 돌려준다. 캐시는 휘발성(TTL 6h).")
    @PostMapping("/applicant/{applicantId}/content")
    public ResponseEntity<InstagramAnalysisResult> addContent(
            @Parameter(description = "지원자 ID") @PathVariable Long applicantId,
            @Parameter(description = "릴스 permalink", required = true)
            @RequestParam String reelUrl) {
        return ResponseEntity.ok(evaluationService.addContent(applicantId, reelUrl));
    }

    @Operation(summary = "지원자 종합 평가",
            description = "쌓인 콘텐츠 transcript를 합쳐 Gemini 1회로 정성 평가한다. 평가 후 캐시 파기.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "평가 성공"),
            @ApiResponse(responseCode = "404", description = "캐시에 콘텐츠 없음", content = @Content),
            @ApiResponse(responseCode = "502", description = "Gemini 호출·해석 실패", content = @Content)
    })
    @PostMapping("/applicant/{applicantId}/evaluate")
    public ResponseEntity<ApplicantEvaluation> evaluate(
            @Parameter(description = "지원자 ID") @PathVariable Long applicantId) {
        return ResponseEntity.ok(evaluationService.evaluate(applicantId));
    }
}
