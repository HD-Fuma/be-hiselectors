package com.fuma.hiselectors.generation.controller;

import com.fuma.hiselectors.generation.dto.GenerationCreateRequest;
import com.fuma.hiselectors.generation.dto.GenerationResponse;
import com.fuma.hiselectors.generation.dto.GenerationStatusUpdateRequest;
import com.fuma.hiselectors.generation.dto.GenerationUpdateRequest;
import com.fuma.hiselectors.generation.service.GenerationAdminService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "기수 관리", description = "셀렉터스 모집 기수 관리 (관리자 전용)")
@RestController
@RequestMapping("/api/admin/generations")
@RequiredArgsConstructor
public class GenerationAdminController {

    private final GenerationAdminService generationAdminService;

    @Operation(summary = "기수 목록 조회")
    @GetMapping
    public ResponseEntity<List<GenerationResponse>> findAll() {
        return ResponseEntity.ok(generationAdminService.findAll());
    }

    @Operation(summary = "기수 상세 조회")
    @ApiResponse(responseCode = "404", description = "기수 없음", content = @Content)
    @GetMapping("/{generationId}")
    public ResponseEntity<GenerationResponse> findOne(@PathVariable Long generationId) {
        return ResponseEntity.ok(generationAdminService.findOne(generationId));
    }

    @Operation(summary = "기수 생성", description = "새 기수는 비활성 상태로 생성한다.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "생성 성공"),
            @ApiResponse(responseCode = "400", description = "요청 값 검증 실패", content = @Content)
    })
    @PostMapping
    public ResponseEntity<GenerationResponse> create(
            @Valid @RequestBody GenerationCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(generationAdminService.create(request));
    }

    @Operation(summary = "기수 정보 수정",
            description = "기수명과 모집·활동 기간을 수정한다. null인 필드는 변경하지 않는다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "수정 성공"),
            @ApiResponse(responseCode = "400", description = "모집 기간 오류", content = @Content),
            @ApiResponse(responseCode = "404", description = "기수 없음", content = @Content),
            @ApiResponse(responseCode = "409", description = "활성 기수 기간 중복", content = @Content)
    })
    @PatchMapping("/{generationId}")
    public ResponseEntity<GenerationResponse> update(
            @PathVariable Long generationId,
            @Valid @RequestBody GenerationUpdateRequest request) {
        return ResponseEntity.ok(generationAdminService.update(generationId, request));
    }

    @Operation(summary = "기수 활성 상태 변경",
            description = "활성화할 때 다른 활성 기수와 모집 기간이 겹치면 변경할 수 없다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "변경 성공"),
            @ApiResponse(responseCode = "404", description = "기수 없음", content = @Content),
            @ApiResponse(responseCode = "409", description = "활성 기수 기간 중복", content = @Content)
    })
    @PatchMapping("/{generationId}/status")
    public ResponseEntity<GenerationResponse> updateStatus(
            @PathVariable Long generationId,
            @Valid @RequestBody GenerationStatusUpdateRequest request) {
        return ResponseEntity.ok(generationAdminService.updateStatus(generationId, request));
    }
}
