package org.github.flowify.oauth.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.github.flowify.common.dto.ApiResponse;
import org.github.flowify.oauth.service.OAuthTokenService;
import org.github.flowify.oauth.service.SlackOAuthService;
import org.github.flowify.user.entity.User;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@Slf4j
@Tag(name = "OAuth 토큰", description = "외부 서비스 OAuth 연동 관리")
@RestController
@RequestMapping("/api/oauth-tokens")
@RequiredArgsConstructor
public class OAuthTokenController {

    private final OAuthTokenService oauthTokenService;
    private final SlackOAuthService slackOAuthService;

    @Value("${app.auth.front-redirect-uri}")
    private String frontRedirectUri;

    @Operation(summary = "연결된 서비스 목록 조회", description = "현재 사용자가 연결한 외부 서비스 목록을 조회합니다.")
    @GetMapping
    public ApiResponse<List<Map<String, Object>>> getConnectedServices(Authentication authentication) {
        User user = (User) authentication.getPrincipal();
        return ApiResponse.ok(oauthTokenService.getConnectedServices(user.getId()));
    }

    @Operation(summary = "외부 서비스 연결", description = "외부 서비스 OAuth 인증 URL을 반환합니다.")
    @PostMapping("/{service}/connect")
    public ApiResponse<Map<String, String>> connectService(Authentication authentication,
                                                           @PathVariable String service) {
        User user = (User) authentication.getPrincipal();
        String authUrl = buildOAuthUrl(service, user.getId());
        return ApiResponse.ok(Map.of("authUrl", authUrl));
    }

    @Operation(summary = "Slack OAuth 콜백", description = "Slack 인증 후 토큰을 교환하고 프론트엔드로 리다이렉트합니다.")
    @GetMapping("/slack/callback")
    public ResponseEntity<Void> slackCallback(@RequestParam String code,
                                              @RequestParam String state) {
        try {
            slackOAuthService.exchangeAndSaveToken(code, state);
            String redirectUrl = frontBaseUrl() + "?service=slack&connected=true";
            return ResponseEntity.status(HttpStatus.FOUND)
                    .header(HttpHeaders.LOCATION, redirectUrl)
                    .build();
        } catch (Exception e) {
            log.error("Slack OAuth callback failed", e);
            String errorUrl = frontBaseUrl() + "?service=slack&error=oauth_failed";
            return ResponseEntity.status(HttpStatus.FOUND)
                    .header(HttpHeaders.LOCATION, errorUrl)
                    .build();
        }
    }

    @Operation(summary = "서비스 연결 해제", description = "외부 서비스 연동을 해제하고 토큰을 삭제합니다.")
    @DeleteMapping("/{service}")
    public ApiResponse<Void> disconnectService(Authentication authentication,
                                               @PathVariable String service) {
        User user = (User) authentication.getPrincipal();
        oauthTokenService.deleteToken(user.getId(), service);
        return ApiResponse.ok();
    }

    private String buildOAuthUrl(String service, String userId) {
        return switch (service) {
            case "slack" -> slackOAuthService.buildAuthorizationUrl(userId);
            default -> throw new IllegalArgumentException("지원하지 않는 서비스: " + service);
        };
    }

    private String frontBaseUrl() {
        // front-redirect-uri에서 path 부분을 제거하고 /oauth/callback으로 변경
        // 예: https://flowify-fe.vercel.app/auth/callback → https://flowify-fe.vercel.app/oauth/callback
        String base = frontRedirectUri.contains("/auth/callback")
                ? frontRedirectUri.replace("/auth/callback", "")
                : frontRedirectUri;
        return base + "/oauth/callback";
    }
}
