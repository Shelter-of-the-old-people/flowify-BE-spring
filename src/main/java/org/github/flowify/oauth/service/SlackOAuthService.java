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
public class SlackOAuthService {

    private final OAuthTokenService oauthTokenService;
    private final TokenEncryptionService tokenEncryptionService;

    @Value("${app.oauth.slack.client-id}")
    private String clientId;

    @Value("${app.oauth.slack.client-secret}")
    private String clientSecret;

    @Value("${app.oauth.slack.redirect-uri}")
    private String redirectUri;

    @Value("${app.oauth.slack.scopes}")
    private String scopes;

    public String buildAuthorizationUrl(String userId) {
        String state = tokenEncryptionService.encrypt(userId);
        String encodedState = URLEncoder.encode(state, StandardCharsets.UTF_8);
        String encodedScopes = URLEncoder.encode(scopes, StandardCharsets.UTF_8);

        return "https://slack.com/oauth/v2/authorize"
                + "?client_id=" + clientId
                + "&scope=" + encodedScopes
                + "&redirect_uri=" + URLEncoder.encode(redirectUri, StandardCharsets.UTF_8)
                + "&state=" + encodedState;
    }

    @SuppressWarnings("unchecked")
    public void exchangeAndSaveToken(String code, String state) {
        String userId = tokenEncryptionService.decrypt(state);

        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("code", code);
        params.add("client_id", clientId);
        params.add("client_secret", clientSecret);
        params.add("redirect_uri", redirectUri);

        WebClient webClient = WebClient.create();
        Map<String, Object> response = webClient.post()
                .uri("https://slack.com/api/oauth.v2.access")
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_FORM_URLENCODED_VALUE)
                .bodyValue(params)
                .retrieve()
                .bodyToMono(Map.class)
                .block();

        if (response == null || !Boolean.TRUE.equals(response.get("ok"))) {
            String error = response != null ? (String) response.get("error") : "unknown";
            log.error("Slack token exchange failed: {}", error);
            throw new BusinessException(ErrorCode.AUTH_OAUTH_FAILED, "Slack 토큰 교환에 실패했습니다: " + error);
        }

        Map<String, Object> authedUser = (Map<String, Object>) response.get("authed_user");
        String accessToken = (String) response.get("access_token");
        String scopeStr = (String) response.get("scope");
        List<String> tokenScopes = scopeStr != null ? List.of(scopeStr.split(",")) : List.of();

        // Slack bot tokens don't expire, but we set a far-future expiry
        Instant expiresAt = Instant.now().plusSeconds(365L * 24 * 3600);

        oauthTokenService.saveToken(userId, "slack", accessToken, null, expiresAt, tokenScopes);

        log.info("Slack OAuth token saved for userId={}", userId);
    }
}
