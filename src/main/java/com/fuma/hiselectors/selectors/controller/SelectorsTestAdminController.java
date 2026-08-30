package com.fuma.hiselectors.selectors.controller;

import com.fuma.hiselectors.application.model.SnsPlatform;
import com.fuma.hiselectors.selectors.dto.SelectorsTestResetResponse;
import com.fuma.hiselectors.selectors.service.SelectorsTestResetService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.security.Principal;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/selectors")
@Tag(name = "관리자 셀렉터스", description = "셀렉터스 목록·상세 조회 (관리자 전용)")
public class SelectorsTestAdminController {

    private final SelectorsTestResetService resetService;

    @Operation(summary = "테스트 계정 리셋",
            description = "플랫폼과 SNS 계정명으로 찾은 셀렉터스와 지원서, 그리고 거기 매달린 "
                    + "콘텐츠·검수·패널티·정산·구매 기록을 모두 물리 삭제해 지원 이전 상태로 되돌린다. "
                    + "로그인 계정은 남기므로 같은 HiID 로 다시 지원할 수 있다. 되돌릴 수 없다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "리셋 성공"),
            @ApiResponse(responseCode = "400", description = "계정명 누락", content = @Content),
            @ApiResponse(responseCode = "404", description = "해당 계정의 지원·셀렉터스 없음",
                    content = @Content)
    })
    @DeleteMapping("/test-reset")
    public ResponseEntity<SelectorsTestResetResponse> resetTestAccount(
            @Parameter(description = "SNS 플랫폼", example = "INSTAGRAM")
            @RequestParam SnsPlatform snsCode,
            @Parameter(description = "SNS 계정명. 앞의 @ 는 있어도 되고 없어도 된다",
                    example = "hiselectors_test")
            @RequestParam String accountId,
            Principal principal) {
        return ResponseEntity.ok(resetService.reset(
                snsCode, accountId, principal == null ? null : principal.getName()));
    }
}
