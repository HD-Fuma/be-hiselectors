package com.fuma.hiselectors.application.controller;

import com.fuma.hiselectors.application.dto.ApplicationMediaCollectionResponse;
import com.fuma.hiselectors.application.dto.ApplicationMediaResponse;
import com.fuma.hiselectors.application.service.ApplicationMediaService;
import io.swagger.v3.oas.annotations.Operation;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/applications/{applicationId}/media")
public class ApplicationMediaAdminController {

    private final ApplicationMediaService applicationMediaService;

    @Operation(summary = "지원자 최근 미디어 수집")
    @PostMapping("/collect")
    public ResponseEntity<ApplicationMediaCollectionResponse> collect(
            @PathVariable Long applicationId) {
        return ResponseEntity.ok(applicationMediaService.collect(applicationId));
    }

    @Operation(summary = "지원자 최신 미디어 3개 조회")
    @GetMapping
    public ResponseEntity<List<ApplicationMediaResponse>> findLatest(
            @PathVariable Long applicationId) {
        return ResponseEntity.ok(applicationMediaService.findLatest(applicationId));
    }
}
