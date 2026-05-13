package org.github.flowify.oauth.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.github.flowify.common.dto.ApiResponse;
import org.github.flowify.oauth.dto.ManualOAuthTokenRequest;
import org.github.flowify.oauth.dto.OAuthTokenSummaryResponse;
import org.github.flowify.oauth.service.ConnectResult;
import org.github.flowify.oauth.service.ExternalServiceConnector;
import org.github.flowify.oauth.service.OAuthTokenService;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Tag(name = "OAuth 토큰", description = "외부 서비스 연결과 토큰 상태를 관리합니다.")
@RestController
@RequestMapping("/api/oauth-tokens")
public class OAuthTokenController {

    private final OAuthTokenService oauthTokenService;
    private final Map<String, ExternalServiceConnector> connectorMap;

    @Value("${app.auth.front-redirect-uri}")
    private String frontRedirectUri;

    public OAuthTokenController(OAuthTokenService oauthTokenService,
                                List<ExternalServiceConnector> connectors) {
        this.oauthTokenService = oauthTokenService;
        this.connectorMap = connectors.stream()
                .collect(Collectors.toMap(
                        ExternalServiceConnector::getServiceName,
                        Function.identity()));
    }

    @Operation(summary = "연결된 서비스 목록 조회", description = "현재 사용자가 연결한 외부 서비스의 토큰 상태를 조회합니다.")
    @GetMapping
    public ApiResponse<List<OAuthTokenSummaryResponse>> getConnectedServices(Authentication authentication) {
        User user = (User) authentication.getPrincipal();
        return ApiResponse.ok(oauthTokenService.getConnectedServices(user.getId()));
    }

    @Operation(summary = "manual token 저장 또는 갱신", description = "계정 페이지에서 사용자가 직접 입력한 토큰을 검증하고 저장합니다.")
    @PutMapping("/{service}/manual")
    public ApiResponse<OAuthTokenSummaryResponse> upsertManualToken(Authentication authentication,
                                                                    @PathVariable String service,
                                                                    @Valid @RequestBody ManualOAuthTokenRequest request) {
        User user = (User) authentication.getPrincipal();
        return ApiResponse.ok(oauthTokenService.upsertManualToken(user.getId(), service, request.getAccessToken()));
    }

    @Operation(summary = "외부 서비스 연결 시작", description = "OAuth redirect 서비스는 인증 URL을, direct connect 서비스는 즉시 연결 결과를 반환합니다.")
    @PostMapping("/{service}/connect")
    public ApiResponse<Map<String, String>> connectService(Authentication authentication,
                                                           @PathVariable String service) {
        User user = (User) authentication.getPrincipal();
        ExternalServiceConnector connector = getConnector(service);
        ConnectResult result = connector.connect(user.getId());

        return switch (result) {
            case ConnectResult.RedirectRequired r ->
                    ApiResponse.ok(Map.of("authUrl", r.authUrl()));
            case ConnectResult.DirectlyConnected d ->
                    ApiResponse.ok(Map.of("connected", "true", "service", d.service()));
        };
    }

    @Operation(summary = "OAuth callback", description = "OAuth 인증 코드를 토큰으로 교환하고 프론트엔드 callback 페이지로 이동합니다.")
    @GetMapping("/{service}/callback")
    public ResponseEntity<Void> oauthCallback(@PathVariable String service,
                                              @RequestParam String code,
                                              @RequestParam String state) {
        ExternalServiceConnector connector = getConnector(service);
        if (!connector.supportsCallback()) {
            throw new IllegalArgumentException(service + " does not support OAuth callback");
        }
        try {
            connector.handleCallback(code, state);
            String redirectUrl = frontBaseUrl() + "?service=" + service + "&connected=true";
            return ResponseEntity.status(HttpStatus.FOUND)
                    .header(HttpHeaders.LOCATION, redirectUrl)
                    .build();
        } catch (Exception e) {
            log.error("{} OAuth callback failed", service, e);
            String errorUrl = frontBaseUrl() + "?service=" + service + "&error=oauth_failed";
            return ResponseEntity.status(HttpStatus.FOUND)
                    .header(HttpHeaders.LOCATION, errorUrl)
                    .build();
        }
    }

    @Operation(summary = "서비스 연결 해제", description = "외부 서비스 연결을 해제하고 저장된 토큰을 삭제합니다.")
    @DeleteMapping("/{service}")
    public ApiResponse<Void> disconnectService(Authentication authentication,
                                               @PathVariable String service) {
        User user = (User) authentication.getPrincipal();
        oauthTokenService.deleteToken(user.getId(), service);
        return ApiResponse.ok();
    }

    private ExternalServiceConnector getConnector(String service) {
        ExternalServiceConnector connector = connectorMap.get(service);
        if (connector == null) {
            throw new IllegalArgumentException("지원하지 않는 서비스: " + service);
        }
        return connector;
    }

    private String frontBaseUrl() {
        String base = frontRedirectUri.contains("/auth/callback")
                ? frontRedirectUri.replace("/auth/callback", "")
                : frontRedirectUri;
        return base + "/oauth/callback";
    }
}