package com.fuma.hiselectors.application.controller;

import com.fuma.hiselectors.application.dto.ApplicationCreateRequest;
import com.fuma.hiselectors.application.dto.ApplicationResponse;
import com.fuma.hiselectors.application.service.ApplicationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.security.Principal;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "지원", description = "셀렉터스 지원서 작성 및 필수 항목 동의")
@RestController
@RequestMapping("/api/applications")
@RequiredArgsConstructor
public class ApplicationController {

    private final ApplicationService applicationService;

    @Operation(summary = "지원서 제출",
            description = "로그인한 유저가 사전에 OAuth 로 인증한 SNS 채널 정보와 필수 동의(개인정보 열람·"
                    + "카카오 알림톡 수신)를 함께 제출해 지원서를 생성한다. 동의 시 policy_agreed_at 에 "
                    + "처리 시각이 기록된다.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "생성 성공"),
            @ApiResponse(responseCode = "400", description = "요청 값 검증 실패(동의 미체크 포함)",
                    content = @Content),
            @ApiResponse(responseCode = "401", description = "로그인 필요", content = @Content),
            @ApiResponse(responseCode = "404", description = "지원자 없음", content = @Content),
            @ApiResponse(responseCode = "409",
                    description = "모집 중인 기수 없음 또는 이미 해당 기수에 지원함", content = @Content)
    })
    @PostMapping
    public ResponseEntity<ApplicationResponse> create(
            Principal principal,
            @Valid @RequestBody ApplicationCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(applicationService.create(principal.getName(), request));
    }
}
