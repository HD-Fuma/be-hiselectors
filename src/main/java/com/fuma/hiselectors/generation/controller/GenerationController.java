package com.fuma.hiselectors.generation.controller;

import com.fuma.hiselectors.generation.dto.GenerationResponse;
import com.fuma.hiselectors.generation.service.GenerationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "기수", description = "모집 기수 조회")
@RestController
@RequestMapping("/api/generations")
@RequiredArgsConstructor
public class GenerationController {

    private final GenerationService generationService;

    @Operation(summary = "현재 활성 기수 조회",
            description = "지금 시점 기준 모집 중(ACTIVE, 기간 내)인 기수를 반환한다. 없으면 409.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "409", description = "모집 중인 기수 없음", content = @Content)
    })
    @SecurityRequirements
    @GetMapping("/active")
    public ResponseEntity<GenerationResponse> getActive() {
        return ResponseEntity.ok(GenerationResponse.from(generationService.getActive()));
    }
}
