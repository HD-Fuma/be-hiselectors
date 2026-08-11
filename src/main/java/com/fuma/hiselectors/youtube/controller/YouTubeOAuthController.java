package com.fuma.hiselectors.youtube.controller;

import com.fuma.hiselectors.exception.BusinessException;
import com.fuma.hiselectors.youtube.config.YouTubeOAuthProperties;
import com.fuma.hiselectors.youtube.dto.YouTubeAuthUrlResponse;
import com.fuma.hiselectors.youtube.service.YouTubeOAuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.net.URI;
import java.security.Principal;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

@Tag(name = "유튜브 인증", description = "본인 유튜브 채널 소유 인증 (OAuth 2.0)")
@RestController
@RequestMapping("/api/youtube/oauth")
@RequiredArgsConstructor
public class YouTubeOAuthController {

    private final YouTubeOAuthService youTubeOAuthService;
    private final YouTubeOAuthProperties properties;

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

    @Operation(summary = "유튜브 OAuth 콜백 (구글이 호출)",
            description = "구글이 code/state 를 붙여 호출하는 엔드포인트. 처리 후 프론트엔드로 리다이렉트한다. "
                    + "클라이언트가 직접 호출하는 API가 아니다.")
    @GetMapping("/callback")
    public ResponseEntity<Void> callback(
            @RequestParam(required = false) String code,
            @RequestParam(required = false) String state,
            @RequestParam(name = "error", required = false) String error) {

        // 사용자가 동의를 거부하면 구글이 code 대신 error 를 보낸다.
        if (error != null || code == null || state == null) {
            return redirect(failUrl(error != null ? error : "invalid_request"));
        }

        try {
            String channelTitle = youTubeOAuthService.verifyChannelOwnership(code, state);
            return redirect(successUrl(channelTitle));
        } catch (BusinessException e) {
            return redirect(failUrl(e.getErrorCode().name()));
        }
    }

    private URI successUrl(String channelTitle) {
        return UriComponentsBuilder.fromUriString(properties.frontendRedirectUri())
                .queryParam("youtube", "success")
                .queryParamIfPresent("channel",
                        java.util.Optional.ofNullable(channelTitle))
                .build()
                .encode()
                .toUri();
    }

    private URI failUrl(String reason) {
        return UriComponentsBuilder.fromUriString(properties.frontendRedirectUri())
                .queryParam("youtube", "fail")
                .queryParam("reason", reason)
                .build()
                .encode()
                .toUri();
    }

    private ResponseEntity<Void> redirect(URI location) {
        return ResponseEntity.status(HttpStatus.FOUND).location(location).build();
    }
}
