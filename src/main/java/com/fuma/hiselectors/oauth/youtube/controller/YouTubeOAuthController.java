package com.fuma.hiselectors.oauth.youtube.controller;

import com.fuma.hiselectors.oauth.youtube.dto.YouTubeAuthUrlResponse;
import com.fuma.hiselectors.oauth.youtube.dto.YouTubeVerifyRequest;
import com.fuma.hiselectors.oauth.youtube.dto.YouTubeVerifyResponse;
import com.fuma.hiselectors.oauth.youtube.service.YouTubeOAuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.security.Principal;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "유튜브 인증", description = "본인 유튜브 채널 소유 인증 (OAuth 2.0)")
@RestController
@RequestMapping("/api/youtube/oauth")
@RequiredArgsConstructor
public class YouTubeOAuthController {

    private final YouTubeOAuthService youTubeOAuthService;

    @Operation(summary = "유튜브 채널 인증 시작",
            description = "로그인한 유저 기준으로 구글 동의 화면 URL을 발급한다. "
                    + "프론트는 '버튼' 클릭 시 이 API를 호출한 뒤, 응답의 authorizationUrl 로 리다이렉트한다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "인증 URL 발급 성공"),
            @ApiResponse(responseCode = "401", description = "로그인 필요",
                    content = @io.swagger.v3.oas.annotations.media.Content)
    })
    @GetMapping("/authorize")
    public ResponseEntity<YouTubeAuthUrlResponse> authorize(Principal principal) {
        String authorizationUrl = youTubeOAuthService.buildAuthorizationUrl(principal.getName());
        return ResponseEntity.ok(YouTubeAuthUrlResponse.of(authorizationUrl));
    }

    @Operation(summary = "유튜브 채널 인증 검증",
            description = "프론트가 구글 콜백에서 받은 code/state 를 넘기면, 백엔드가 소유를 확인하고 "
                    + "구글 계정이 소유한 채널 목록(각각 채널ID/이름/구독자수/verificationToken)을 반환한다. "
                    + "채널이 여러 개면 사용자가 하나를 골라 그 채널의 verificationToken 으로 지원서를 제출한다. "
                    + "소유 증명은 OAuth 자체로 끝. (저장하지 않음)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "인증 성공 (채널 목록 반환)"),
            @ApiResponse(responseCode = "400", description = "요청 값 검증 실패",
                    content = @io.swagger.v3.oas.annotations.media.Content),
            @ApiResponse(responseCode = "401", description = "로그인 필요 / state 불일치",
                    content = @io.swagger.v3.oas.annotations.media.Content),
            @ApiResponse(responseCode = "404", description = "본인 유튜브 채널 없음",
                    content = @io.swagger.v3.oas.annotations.media.Content)
    })
    @PostMapping("/verify")
    public ResponseEntity<YouTubeVerifyResponse> verify(@Valid @RequestBody YouTubeVerifyRequest request,
                                                        Principal principal) {
        YouTubeVerifyResponse response = youTubeOAuthService.verifyChannelOwnership(
                request.code(), request.state(), principal.getName());
        return ResponseEntity.ok(response);
    }
}
