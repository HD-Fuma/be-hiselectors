package com.fuma.hiselectors.kakao.controller;

import com.fuma.hiselectors.kakao.dto.KakaoAuthUrlResponse;
import com.fuma.hiselectors.kakao.dto.KakaoOAuthConnectRequest;
import com.fuma.hiselectors.kakao.dto.KakaoRecipientConnectionResponse;
import com.fuma.hiselectors.kakao.dto.KakaoRecipientConnectionStatusResponse;
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
@RequestMapping("/api/kakao/oauth")
@RequiredArgsConstructor
@Tag(name = "카카오 수신 인증", description = "카카오 메시지 수신용 사용자 계정 연동 및 상태 조회")
public class KakaoOAuthController {

    private final KakaoOAuthService oauthService;

    @GetMapping("/authorize")
    public ResponseEntity<KakaoAuthUrlResponse> authorize(Principal principal) {
        return ResponseEntity.ok(new KakaoAuthUrlResponse(oauthService.buildAuthorizationUrl(
                principal.getName(), KakaoConnectionType.RECIPIENT)));
    }

    @GetMapping("/status")
    public ResponseEntity<KakaoRecipientConnectionStatusResponse> status(Principal principal) {
        return ResponseEntity.ok(oauthService.getRecipientStatus(principal.getName()));
    }

    @PostMapping("/connect")
    public ResponseEntity<KakaoRecipientConnectionResponse> connect(
            @Valid @RequestBody KakaoOAuthConnectRequest request, Principal principal) {
        return ResponseEntity.ok(oauthService.connectRecipient(
                request.code(), request.state(), principal.getName()));
    }
}
