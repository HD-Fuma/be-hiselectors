package com.fuma.hiselectors.creator.controller;

import com.fuma.hiselectors.creator.discovery.DiscoveryCoverageService;
import com.fuma.hiselectors.creator.discovery.dto.DiscoveryCoverageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "크리에이터 발굴 분석", description = "키워드별 중복 관측을 활용한 발굴 포화도")
@RestController
@RequestMapping("/api/admin/discovery/coverage")
@RequiredArgsConstructor
public class DiscoveryCoverageController {

    private final DiscoveryCoverageService discoveryCoverageService;

    @Operation(summary = "카테고리별 YouTube 발굴 포화도 조회")
    @GetMapping
    public ResponseEntity<List<DiscoveryCoverageResponse>> findAll() {
        return ResponseEntity.ok(discoveryCoverageService.findAll());
    }
}
