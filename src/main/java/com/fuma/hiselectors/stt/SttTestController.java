package com.fuma.hiselectors.stt;

import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/stt")
@RequiredArgsConstructor
public class SttTestController {

    private final SttService sttService;

    @GetMapping("/transcribe")
    public ResponseEntity<Map<String, String>> transcribe(
            @RequestParam(defaultValue = "YOUTUBE") String snsCode,
            @RequestParam String snsContentId) {
        return ResponseEntity.ok(
                Map.of("transcript", sttService.transcribe(snsCode, snsContentId)));
    }
}
