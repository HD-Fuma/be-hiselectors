package com.fuma.hiselectors.auth.controller;

import com.fuma.hiselectors.auth.dto.LoginRequest;
import com.fuma.hiselectors.auth.dto.TokenResponse;
import com.fuma.hiselectors.auth.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    //일반 유저 로그인 → JWT 발급
    @PostMapping("/user/login")
    public ResponseEntity<TokenResponse> userLogin(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.userLogin(request));
    }

    //관리자 로그인 → JWT 발급
    @PostMapping("/admin/login")
    public ResponseEntity<TokenResponse> adminLogin(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.adminLogin(request));
    }
}
