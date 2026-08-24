package com.fuma.hiselectors.user.controller;

import com.fuma.hiselectors.user.dto.UserMeResponse;
import com.fuma.hiselectors.user.service.UserQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.security.Principal;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "회원", description = "로그인한 사용자 회원정보 조회")
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserQueryService userQueryService;

    @Operation(summary = "내 회원정보 조회",
            description = "아이디, 이름, 이메일, 휴대폰, 알림톡 수신 동의 여부를 조회한다.")
    @GetMapping("/me")
    public ResponseEntity<UserMeResponse> getMe(Principal principal) {
        return ResponseEntity.ok(userQueryService.getMe(principal.getName()));
    }
}
