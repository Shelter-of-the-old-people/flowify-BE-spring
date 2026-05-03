package org.github.flowify.oauth.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.github.flowify.common.exception.BusinessException;
import org.github.flowify.common.exception.ErrorCode;
import org.github.flowify.oauth.dto.TokenRefreshResult;
import org.github.flowify.oauth.entity.OAuthToken;
import org.github.flowify.oauth.repository.OAuthTokenRepository;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class OAuthTokenService {

    private static final long REFRESH_THRESHOLD_SECONDS = 300; // 5분

    private final OAuthTokenRepository oauthTokenRepository;
    private final TokenEncryptionService tokenEncryptionService;
    private final List<OAuthTokenRefresher> tokenRefreshers;

    private Map<String, OAuthTokenRefresher> refresherMap;

    @jakarta.annotation.PostConstruct
    private void initRefreshers() {
        refresherMap = tokenRefreshers == null ? Collections.emptyMap() : tokenRefreshers.stream()
                .collect(Collectors.toMap(OAuthTokenRefresher::getServiceName, Function.identity()));
    }

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
            throw new BusinessException(ErrorCode.OAUTH_TOKEN_EXPIRED,
                    "Refresh token이 없습니다. 서비스 재연결이 필요합니다.");
        }

        if (refresherMap == null) {
            initRefreshers();
        }
        OAuthTokenRefresher refresher = refresherMap.get(token.getService());
        if (refresher == null) {
            log.warn("토큰 갱신을 지원하지 않는 서비스: {}", token.getService());
            throw new BusinessException(ErrorCode.OAUTH_TOKEN_EXPIRED,
                    token.getService() + " 서비스 토큰 갱신을 지원하지 않습니다. 재연결이 필요합니다.");
        }

        String decryptedRefreshToken = tokenEncryptionService.decrypt(token.getRefreshToken());
        TokenRefreshResult result = refresher.refresh(decryptedRefreshToken);

        token.setAccessToken(tokenEncryptionService.encrypt(result.getAccessToken()));
        token.setExpiresAt(Instant.now().plusSeconds(result.getExpiresIn() != null ? result.getExpiresIn() : 3600));
        if (result.getRefreshToken() != null) {
            token.setRefreshToken(tokenEncryptionService.encrypt(result.getRefreshToken()));
        }
        oauthTokenRepository.save(token);

        log.info("토큰 갱신 완료: userId={}, service={}", token.getUserId(), token.getService());
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
