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
}
