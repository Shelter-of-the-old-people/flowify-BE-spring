package org.github.flowify.oauth.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.github.flowify.common.exception.BusinessException;
import org.github.flowify.common.exception.ErrorCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.client.WebClient;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class GoogleDriveConnector implements ExternalServiceConnector {

    private final OAuthTokenService oauthTokenService;
    private final TokenEncryptionService tokenEncryptionService;

    @Value("${app.oauth.google-drive.client-id}")
    private String clientId;

    @Value("${app.oauth.google-drive.client-secret}")
    private String clientSecret;

    @Value("${app.oauth.google-drive.redirect-uri}")
    private String redirectUri;

    @Value("${app.oauth.google-drive.scopes}")
    private String scopes;

    @Override
    public String getServiceName() {
        return "google-drive";
    }

    @Override
    public ConnectResult connect(String userId) {
        String state = tokenEncryptionService.encrypt(userId);
        String encodedState = URLEncoder.encode(state, StandardCharsets.UTF_8);
        String encodedScopes = URLEncoder.encode(scopes, StandardCharsets.UTF_8);

        String authUrl = "https://accounts.google.com/o/oauth2/v2/auth"
                + "?client_id=" + clientId
                + "&redirect_uri=" + URLEncoder.encode(redirectUri, StandardCharsets.UTF_8)
                + "&response_type=code"
                + "&scope=" + encodedScopes
                + "&access_type=offline"
                + "&prompt=consent"
                + "&state=" + encodedState;

        return new ConnectResult.RedirectRequired(authUrl);
    }

    @Override
    public boolean supportsCallback() {
        return true;
    }

    @SuppressWarnings("unchecked")
    @Override
    public void handleCallback(String code, String state) {
        String userId = tokenEncryptionService.decrypt(state);

        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("code", code);
        params.add("client_id", clientId);
        params.add("client_secret", clientSecret);
        params.add("redirect_uri", redirectUri);
        params.add("grant_type", "authorization_code");

        WebClient webClient = WebClient.create();
        Map<String, Object> response = webClient.post()
                .uri("https://oauth2.googleapis.com/token")
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_FORM_URLENCODED_VALUE)
                .bodyValue(params)
                .retrieve()
                .bodyToMono(Map.class)
                .block();

        if (response == null || !response.containsKey("access_token")) {
            String error = response != null ? String.valueOf(response.get("error")) : "unknown";
            log.error("Google Drive token exchange failed: {}", error);
            throw new BusinessException(ErrorCode.AUTH_OAUTH_FAILED,
                    "Google Drive 토큰 교환에 실패했습니다: " + error);
        }

        String accessToken = (String) response.get("access_token");
        String refreshToken = (String) response.get("refresh_token");
        Integer expiresIn = (Integer) response.get("expires_in");
        String scopeStr = (String) response.get("scope");

        Instant expiresAt = Instant.now().plusSeconds(expiresIn != null ? expiresIn : 3600);
        List<String> tokenScopes = scopeStr != null ? List.of(scopeStr.split(" ")) : List.of();

        oauthTokenService.saveToken(userId, "google-drive", accessToken, refreshToken, expiresAt, tokenScopes);

        log.info("Google Drive OAuth token saved for userId={}", userId);
    }
}
