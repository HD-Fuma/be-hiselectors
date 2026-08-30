package com.fuma.hiselectors.stt.test;

import com.fuma.hiselectors.stt.YoutubeSttClient;
import com.fuma.hiselectors.stt.YoutubeSttExecutionResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "YouTube STT 측정", description = "개별 YouTube 콘텐츠의 Gemini STT 시간·토큰·재시도 측정")
@RestController
@RequestMapping("/api/stt/test/youtube")
@RequiredArgsConstructor
public class YoutubeSttTestController {

    private final YoutubeSttClient youtubeSttClient;

    @Operation(summary = "YouTube 콘텐츠 STT 측정")
    @PostMapping("/measure")
    public ResponseEntity<YoutubeSttTestResponse> measure(
            @Valid @RequestBody YoutubeSttTestRequest request) {
        YoutubeSttExecutionResult execution =
                youtubeSttClient.transcribeMeasured(request.videoId());
        return ResponseEntity.ok(YoutubeSttTestResponse.from(request.videoId(), execution));
    }
}
