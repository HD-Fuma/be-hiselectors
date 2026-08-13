package com.fuma.hiselectors.auth.controller;

import com.fuma.hiselectors.auth.dto.LoginRequest;
import com.fuma.hiselectors.auth.dto.TokenResponse;
import com.fuma.hiselectors.auth.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "인증", description = "유저/관리자 로그인 및 JWT 발급")
@SecurityRequirements
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @Operation(summary = "유저 로그인",
            description = "일반 사용자(Users) 계정으로 로그인하고 JWT 액세스 토큰을 발급받는다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "로그인 성공 (토큰 발급)"),
            @ApiResponse(responseCode = "400", description = "요청 값 검증 실패", content = @io.swagger.v3.oas.annotations.media.Content),
            @ApiResponse(responseCode = "401", description = "아이디 또는 비밀번호 불일치", content = @io.swagger.v3.oas.annotations.media.Content)
    })
    @PostMapping("/user/login")
    public ResponseEntity<TokenResponse> userLogin(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    content = @Content(examples = @ExampleObject(
                            value = "{\n  \"loginId\": \"hiuser1\",\n  \"password\": \"0000\"\n}")))
            @Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.userLogin(request));
    }

    @Operation(summary = "관리자 로그인",
            description = "관리자(Admin) 계정으로 로그인하고 JWT 액세스 토큰을 발급받는다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "로그인 성공 (토큰 발급)"),
            @ApiResponse(responseCode = "400", description = "요청 값 검증 실패", content = @io.swagger.v3.oas.annotations.media.Content),
            @ApiResponse(responseCode = "401", description = "아이디 또는 비밀번호 불일치", content = @io.swagger.v3.oas.annotations.media.Content)
    })
    @PostMapping("/admin/login")
    public ResponseEntity<TokenResponse> adminLogin(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    content = @Content(examples = @ExampleObject(
                            value = "{\n  \"loginId\": \"admin1\",\n  \"password\": \"0000\"\n}")))
            @Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.adminLogin(request));
    }
}
