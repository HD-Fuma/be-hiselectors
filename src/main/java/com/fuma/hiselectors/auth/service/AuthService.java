package com.fuma.hiselectors.auth.service;

import com.fuma.hiselectors.admin.model.Admin;
import com.fuma.hiselectors.admin.repository.AdminRepository;
import com.fuma.hiselectors.auth.dto.LoginRequest;
import com.fuma.hiselectors.auth.dto.TokenResponse;
import com.fuma.hiselectors.exception.BusinessException;
import com.fuma.hiselectors.security.jwt.JwtTokenProvider;
import com.fuma.hiselectors.user.model.User;
import com.fuma.hiselectors.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuthService {

    private final UserRepository userRepository;
    private final AdminRepository adminRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;

    /** 일반 유저 로그인 (Users 테이블, hi_id/hi_password). */
    public TokenResponse userLogin(LoginRequest request) {
        User user = userRepository.findByHiId(request.loginId())
                .orElseThrow(this::unauthorized);

        if (!passwordEncoder.matches(request.password(), user.getHiPassword())) {
            throw unauthorized();
        }

        String token = jwtTokenProvider.createToken(user.getHiId(), "USER");
        return TokenResponse.of(token, user.getHiId(), "USER");
    }

    /** 관리자 로그인 (Admin 테이블, login_id/password). */
    public TokenResponse adminLogin(LoginRequest request) {
        Admin admin = adminRepository.findByLoginId(request.loginId())
                .orElseThrow(this::unauthorized);

        if (!passwordEncoder.matches(request.password(), admin.getPassword())) {
            throw unauthorized();
        }

        String token = jwtTokenProvider.createToken(admin.getLoginId(), "ADMIN");
        return TokenResponse.of(token, admin.getLoginId(), "ADMIN");
    }

    private BusinessException unauthorized() {
        return new BusinessException(HttpStatus.UNAUTHORIZED, "아이디 또는 비밀번호가 올바르지 않습니다.");
    }
}
