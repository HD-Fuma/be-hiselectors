package com.fuma.hiselectors.config;

import com.fuma.hiselectors.security.jwt.JwtAuthenticationEntryPoint;
import com.fuma.hiselectors.security.jwt.JwtAuthenticationFilter;
import com.fuma.hiselectors.security.jwt.JwtTokenProvider;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtTokenProvider jwtTokenProvider;
    private final JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;
    private final Environment environment;

    // 인증 없이 접근을 허용할 경로 (헬스체크, 로그인, 정적 리소스 등)
    private static final String[] PUBLIC_ENDPOINTS = {
            "/",
            "/index.html",           // 루트(/)가 forward 되는 대상 → 반드시 허용
            "/youtube-test.html",    // OAuth 흐름 수동 테스트용 정적 페이지
            "/instagram-test.html",  // OAuth 흐름 수동 테스트용 정적 페이지
            "/kakao-test",          // local 프로필에서만 Controller가 제공
            "/favicon.ico",
            "/css/**", "/js/**", "/images/**",
            "/actuator/health",
            "/error",
            "/api/auth/**",
            "/api/purchases/**",
            "/api/generations/active",  // 모집 중 기수 확인은 로그인 전에도 가능
            "/api/shops/**",            // 셀렉터스 샵 공개 조회

            "/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**",
    };

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .exceptionHandling(ex -> ex.authenticationEntryPoint(jwtAuthenticationEntryPoint))
                .authorizeHttpRequests(auth -> {
                    auth.requestMatchers(PUBLIC_ENDPOINTS).permitAll();
                    // STT 테스트 경로는 로컬에서만 공개. 운영에선 관리자만(미인증+과금 남용 방지).
                    if (environment.matchesProfiles("local")) {
                        auth.requestMatchers("/stt-test.html", "/api/stt/**").permitAll();
                    }
                    auth.requestMatchers("/api/admin/**").hasRole("ADMIN");
                    auth.requestMatchers("/api/stt/**").hasRole("ADMIN");
                    auth.anyRequest().authenticated();
                })
                .addFilterBefore(new JwtAuthenticationFilter(jwtTokenProvider),
                        UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of("*"));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
