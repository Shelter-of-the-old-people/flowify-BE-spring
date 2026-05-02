package org.github.flowify.oauth.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.github.flowify.common.exception.BusinessException;
import org.github.flowify.common.exception.ErrorCode;
import org.github.flowify.oauth.entity.OAuthToken;
import org.github.flowify.oauth.repository.OAuthTokenRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class GoogleDriveTokenRefreshService {

    private final OAuthTokenRepository oauthTokenRepository;
    private final TokenEncryptionService tokenEncryptionService;

    @Value("${app.oauth.google-drive.client-id}")
    private String clientId;

    @Value("${app.oauth.google-drive.client-secret}")
    private String clientSecret;

    @SuppressWarnings("unchecked")
    public void refresh(OAuthToken token) {
        String encryptedRefreshToken = token.getRefreshToken();
        if (encryptedRefreshToken == null || encryptedRefreshToken.isBlank()) {
            throw new BusinessException(ErrorCode.OAUTH_TOKEN_EXPIRED);
        }

        String refreshToken = tokenEncryptionService.decrypt(encryptedRefreshToken);

        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("client_id", clientId);
        params.add("client_secret", clientSecret);
        params.add("refresh_token", refreshToken);
        params.add("grant_type", "refresh_token");

        Map<String, Object> response;
        try {
            response = WebClient.create()
                    .post()
                    .uri("https://oauth2.googleapis.com/token")
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_FORM_URLENCODED_VALUE)
                    .bodyValue(params)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();
        } catch (Exception e) {
            log.error("Google Drive token refresh failed for userId={}", token.getUserId(), e);
            throw new BusinessException(ErrorCode.OAUTH_TOKEN_EXPIRED,
                    "Google Drive access token refresh failed.");
        }

        if (response == null || !response.containsKey("access_token")) {
            log.error("Google Drive token refresh returned invalid response for userId={}, response={}",
                    token.getUserId(), response);
            throw new BusinessException(ErrorCode.OAUTH_TOKEN_EXPIRED,
                    "Google Drive access token refresh failed.");
        }

        String accessToken = String.valueOf(response.get("access_token"));
        String nextRefreshToken = response.get("refresh_token") instanceof String value && !value.isBlank()
                ? value
                : refreshToken;
        Number expiresIn = response.get("expires_in") instanceof Number value ? value : 3600;
        String scopeStr = response.get("scope") instanceof String value ? value : null;

        token.setAccessToken(tokenEncryptionService.encrypt(accessToken));
        token.setRefreshToken(tokenEncryptionService.encrypt(nextRefreshToken));
        token.setExpiresAt(Instant.now().plusSeconds(expiresIn.longValue()));
        if (scopeStr != null && !scopeStr.isBlank()) {
            token.setScopes(List.of(scopeStr.split(" ")));
        }

        oauthTokenRepository.save(token);
    }
}
