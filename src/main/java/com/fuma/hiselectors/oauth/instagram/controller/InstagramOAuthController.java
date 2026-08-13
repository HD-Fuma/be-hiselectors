package com.fuma.hiselectors.oauth.instagram.controller;

import com.fuma.hiselectors.oauth.instagram.dto.InstagramAuthUrlResponse;
import com.fuma.hiselectors.oauth.instagram.dto.InstagramVerifyRequest;
import com.fuma.hiselectors.oauth.instagram.dto.InstagramVerifyResponse;
import com.fuma.hiselectors.oauth.instagram.service.InstagramOAuthService;
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

@Tag(name = "인스타그램 인증", description = "본인 인스타그램 계정 소유 인증 (OAuth 2.0)")
@RestController
@RequestMapping("/api/instagram/oauth")
@RequiredArgsConstructor
public class InstagramOAuthController {

    private final InstagramOAuthService instagramOAuthService;

    @Operation(summary = "인스타그램 계정 인증 시작",
            description = "로그인한 유저 기준으로 인스타그램 동의 화면 URL을 발급한다. "
                    + "프론트는 '버튼' 클릭 시 이 API를 호출한 뒤, 응답의 authorizationUrl 로 리다이렉트한다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "인증 URL 발급 성공"),
            @ApiResponse(responseCode = "401", description = "로그인 필요",
                    content = @io.swagger.v3.oas.annotations.media.Content)
    })
    @GetMapping("/authorize")
    public ResponseEntity<InstagramAuthUrlResponse> authorize(Principal principal) {
        String authorizationUrl = instagramOAuthService.buildAuthorizationUrl(principal.getName());
        return ResponseEntity.ok(InstagramAuthUrlResponse.of(authorizationUrl));
    }

    @Operation(summary = "인스타그램 계정 인증 검증",
            description = "프론트가 인스타그램 콜백에서 받은 code/state 를 넘기면, 백엔드가 소유를 확인하고 "
                    + "계정 정보(계정ID/사용자명/팔로워수)를 반환한다. 소유 증명은 OAuth 자체로 끝. (저장하지 않음)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "인증 성공 (계정 정보 반환)"),
            @ApiResponse(responseCode = "400", description = "요청 값 검증 실패",
                    content = @io.swagger.v3.oas.annotations.media.Content),
            @ApiResponse(responseCode = "401", description = "로그인 필요 / state 불일치",
                    content = @io.swagger.v3.oas.annotations.media.Content),
            @ApiResponse(responseCode = "404", description = "본인 인스타그램 계정 없음",
                    content = @io.swagger.v3.oas.annotations.media.Content)
    })
    @PostMapping("/verify")
    public ResponseEntity<InstagramVerifyResponse> verify(@Valid @RequestBody InstagramVerifyRequest request,
                                                          Principal principal) {
        InstagramVerifyResponse response = instagramOAuthService.verifyAccountOwnership(
                request.code(), request.state(), principal.getName());
        return ResponseEntity.ok(response);
    }
}
