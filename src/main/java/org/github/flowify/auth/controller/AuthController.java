package org.github.flowify.auth.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.github.flowify.auth.dto.LoginResponse;
import org.github.flowify.auth.dto.TokenRefreshRequest;
import org.github.flowify.auth.service.AuthService;
import org.github.flowify.common.dto.ApiResponse;
import org.github.flowify.user.entity.User;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "인증", description = "Google SSO 로그인 및 JWT 토큰 관리")
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @Value("${app.auth.front-redirect-uri}")
    private String frontRedirectUri;

    @Operation(summary = "Google 로그인", description = "Google OAuth2 로그인 페이지로 리다이렉트합니다.")
    @GetMapping("/google")
    public ResponseEntity<Void> googleLogin() {
        String googleLoginUrl = authService.getGoogleLoginUrl();
        return ResponseEntity.status(HttpStatus.FOUND)
                .header(HttpHeaders.LOCATION, googleLoginUrl)
                .build();
    }

    @Operation(summary = "Google OAuth 콜백", description = "Google 인증 코드를 받아 JWT 토큰을 발급하고 프론트엔드로 리다이렉트합니다.")
    @GetMapping("/google/callback")
    public ResponseEntity<Void> googleCallback(
            @Parameter(description = "Google 인증 코드") @RequestParam String code) {
        LoginResponse loginResponse = authService.processGoogleLogin(code);
        String redirectUrl = frontRedirectUri
                + "?accessToken=" + loginResponse.getAccessToken()
                + "&refreshToken=" + loginResponse.getRefreshToken();
        return ResponseEntity.status(HttpStatus.FOUND)
                .header(HttpHeaders.LOCATION, redirectUrl)
                .build();
    }

    @Operation(summary = "토큰 갱신", description = "Refresh Token으로 새 Access Token을 발급합니다.")
    @PostMapping("/refresh")
    public ApiResponse<LoginResponse> refreshToken(@Valid @RequestBody TokenRefreshRequest request) {
        LoginResponse loginResponse = authService.refreshAccessToken(request.getRefreshToken());
        return ApiResponse.ok(loginResponse);
    }

    @Operation(summary = "로그아웃", description = "Refresh Token을 무효화하여 로그아웃합니다.")
    @PostMapping("/logout")
    public ApiResponse<Void> logout(Authentication authentication) {
        User user = (User) authentication.getPrincipal();
        authService.logout(user.getId());
        return ApiResponse.ok();
    }
}