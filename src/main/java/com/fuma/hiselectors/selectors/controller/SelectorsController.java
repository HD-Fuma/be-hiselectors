package com.fuma.hiselectors.selectors.controller;

import com.fuma.hiselectors.application.model.SnsPlatform;
import com.fuma.hiselectors.penalty.model.PenaltyStatus;
import com.fuma.hiselectors.selectors.dto.SelectorsDetailResponse;
import com.fuma.hiselectors.selectors.dto.SelectorsPenaltyResponse;
import com.fuma.hiselectors.selectors.dto.SelectorsSummary;
import com.fuma.hiselectors.selectors.service.SelectorsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 셀렉터스 조회 API.
 *
 * <p>{@code /api/admin/**} 은 SecurityConfig 에서 ROLE_ADMIN 으로 제한되어 있다.
 */
@Tag(name = "관리자 셀렉터스", description = "셀렉터스 목록·상세 조회 (관리자 전용)")
@RestController
@RequestMapping("/api/admin/selectors")
@RequiredArgsConstructor
public class SelectorsController {

    private final SelectorsService selectorsService;

    @Operation(summary = "셀렉터스 목록 조회",
            description = "비워 둔 조건은 적용되지 않는다. 탈퇴·제명된 셀렉터스는 제외된다.")
    @GetMapping
    public ResponseEntity<Page<SelectorsSummary>> search(

            @Parameter(description = "역할 코드", example = "ACTIVE")
            @RequestParam(required = false) String roleId,

            @Parameter(description = "기수 ID. 해당 기수에 참여한 셀렉터스만")
            @RequestParam(required = false) Long generationId,

            @Parameter(description = "닉네임 부분 일치")
            @RequestParam(required = false) String nickname,

            @Parameter(description = "SNS 플랫폼. 해당 플랫폼 계정을 가진 셀렉터스만",
                    example = "YOUTUBE")
            @RequestParam(required = false) SnsPlatform snsCode,

            @PageableDefault(size = 20) Pageable pageable) {

        return ResponseEntity.ok(selectorsService.search(
                roleId, generationId, nickname, snsCode, pageable));
    }

    @Operation(summary = "패널티 및 블랙리스트 대상 조회",
            description = "패널티 보유 셀렉터스를 조회한다. 누적 3회 이상은 블랙리스트 대상이다.")
    @GetMapping("/penalties")
    public ResponseEntity<Page<SelectorsPenaltyResponse>> findPenalties(
            @RequestParam(required = false) Long generationId,
            @RequestParam(required = false) PenaltyStatus status,
            @RequestParam(defaultValue = "false") boolean blacklistOnly,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(selectorsService.findPenalties(
                generationId, status, blacklistOnly, pageable));
    }

    @Operation(summary = "셀렉터스 상세 조회",
            description = "기본 정보와 참여 기수 이력, SNS 계정 목록을 함께 돌려준다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "404", description = "셀렉터스 없음", content = @Content)
    })
    @GetMapping("/{selectorsId}")
    public ResponseEntity<SelectorsDetailResponse> findDetail(
            @Parameter(description = "셀렉터스 ID", example = "1")
            @PathVariable Long selectorsId) {

        return ResponseEntity.ok(selectorsService.findDetail(selectorsId));
    }
}
