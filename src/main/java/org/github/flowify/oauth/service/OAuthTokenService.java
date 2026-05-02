package org.github.flowify.oauth.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.github.flowify.common.exception.BusinessException;
import org.github.flowify.common.exception.ErrorCode;
import org.github.flowify.oauth.entity.OAuthToken;
import org.github.flowify.oauth.repository.OAuthTokenRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class OAuthTokenService {

    private static final long REFRESH_THRESHOLD_SECONDS = 300;

    private final OAuthTokenRepository oauthTokenRepository;
    private final TokenEncryptionService tokenEncryptionService;
    private final GoogleDriveTokenRefreshService googleDriveTokenRefreshService;

    public List<Map<String, Object>> getConnectedServices(String userId) {
        return oauthTokenRepository.findByUserId(userId).stream()
                .map(token -> Map.<String, Object>of(
                        "service", token.getService(),
                        "connected", true,
                        "expiresAt", token.getExpiresAt() != null ? token.getExpiresAt().toString() : ""
                ))
                .toList();
    }

    public void saveToken(String userId, String service, String accessToken,
                          String refreshToken, Instant expiresAt, List<String> scopes) {
        OAuthToken oauthToken = oauthTokenRepository.findByUserIdAndService(userId, service)
                .orElse(OAuthToken.builder()
                        .userId(userId)
                        .service(service)
                        .build());

        oauthToken.setAccessToken(tokenEncryptionService.encrypt(accessToken));
        if (refreshToken != null) {
            oauthToken.setRefreshToken(tokenEncryptionService.encrypt(refreshToken));
        }
        oauthToken.setExpiresAt(expiresAt);
        oauthToken.setScopes(scopes);

        oauthTokenRepository.save(oauthToken);
    }

    public String getDecryptedToken(String userId, String service) {
        OAuthToken token = oauthTokenRepository.findByUserIdAndService(userId, service)
                .orElseThrow(() -> new BusinessException(ErrorCode.OAUTH_NOT_CONNECTED));

        if (isTokenExpiringSoon(token)) {
            refreshTokenIfNeeded(token);
        }

        return tokenEncryptionService.decrypt(token.getAccessToken());
    }

    public void refreshTokenIfNeeded(OAuthToken token) {
        if (token.getRefreshToken() == null) {
            throw new BusinessException(ErrorCode.OAUTH_TOKEN_EXPIRED);
        }

        switch (token.getService()) {
            case "google_drive" -> googleDriveTokenRefreshService.refresh(token);
            default -> log.warn("Token refresh is required but not implemented yet: userId={}, service={}",
                    token.getUserId(), token.getService());
        }
    }

    public void deleteToken(String userId, String service) {
        oauthTokenRepository.deleteByUserIdAndService(userId, service);
    }

    private boolean isTokenExpiringSoon(OAuthToken token) {
        if (token.getExpiresAt() == null) {
            return false;
        }
        return Instant.now().plusSeconds(REFRESH_THRESHOLD_SECONDS).isAfter(token.getExpiresAt());
    }
}
