package com.fuma.hiselectors.kakao.controller;

import com.fuma.hiselectors.kakao.dto.KakaoAuthUrlResponse;
import com.fuma.hiselectors.kakao.dto.KakaoOAuthConnectRequest;
import com.fuma.hiselectors.kakao.dto.KakaoSenderConnectionResponse;
import com.fuma.hiselectors.kakao.oauth.KakaoConnectionType;
import com.fuma.hiselectors.kakao.service.KakaoOAuthService;
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

@RestController
@RequestMapping("/api/admin/kakao/oauth")
@RequiredArgsConstructor
@Tag(name = "카카오 발신자 인증", description = "카카오 메시지 발신용 관리자 계정 연동 (관리자 전용)")
public class KakaoSenderOAuthController {

    private final KakaoOAuthService oauthService;

    @GetMapping("/authorize")
    public ResponseEntity<KakaoAuthUrlResponse> authorize(Principal principal) {
        return ResponseEntity.ok(new KakaoAuthUrlResponse(oauthService.buildAuthorizationUrl(
                principal.getName(), KakaoConnectionType.SENDER)));
    }

    @PostMapping("/connect")
    public ResponseEntity<KakaoSenderConnectionResponse> connect(
            @Valid @RequestBody KakaoOAuthConnectRequest request, Principal principal) {
        return ResponseEntity.ok(oauthService.connectSender(
                request.code(), request.state(), principal.getName()));
    }
}
