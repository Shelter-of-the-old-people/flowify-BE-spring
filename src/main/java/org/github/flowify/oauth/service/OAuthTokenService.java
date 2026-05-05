package org.github.flowify.oauth.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.github.flowify.common.exception.BusinessException;
import org.github.flowify.common.exception.ErrorCode;
import org.github.flowify.oauth.dto.TokenRefreshResult;
import org.github.flowify.oauth.entity.OAuthToken;
import org.github.flowify.oauth.repository.OAuthTokenRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class OAuthTokenService {

    private static final long REFRESH_THRESHOLD_SECONDS = 300;
    private static final Map<String, String> TOKEN_SERVICE_ALIASES = Map.of(
            "google_sheets", "google_drive"
    );

    /**
     * alias 서비스가 원본 토큰에 요구하는 scope 목록
     */
    private static final Map<String, List<String>> ALIAS_REQUIRED_SCOPES = Map.of(
            "google_sheets", List.of("https://www.googleapis.com/auth/spreadsheets")
    );

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
        List<OAuthToken> tokens = oauthTokenRepository.findByUserId(userId);
        List<Map<String, Object>> result = new ArrayList<>();

        // 실제 저장된 토큰 추가
        for (OAuthToken token : tokens) {
            Map<String, Object> entry = new HashMap<>();
            entry.put("service", token.getService());
            entry.put("connected", true);
            entry.put("expiresAt", token.getExpiresAt() != null ? token.getExpiresAt().toString() : "");
            result.add(entry);
        }

        // alias 서비스 추가 (원본 토큰이 있고 scope가 충분한 경우)
        for (Map.Entry<String, String> alias : TOKEN_SERVICE_ALIASES.entrySet()) {
            String aliasService = alias.getKey();
            String originService = alias.getValue();

            // alias 서비스가 이미 직접 연결되어 있으면 skip
            boolean alreadyConnected = tokens.stream()
                    .anyMatch(t -> t.getService().equals(aliasService));
            if (alreadyConnected) {
                continue;
            }

            // 원본 토큰이 있는지 확인
            OAuthToken originToken = tokens.stream()
                    .filter(t -> t.getService().equals(originService))
                    .findFirst()
                    .orElse(null);

            if (originToken == null) {
                continue;
            }

            // 필요한 scope가 있는지 확인
            List<String> requiredScopes = ALIAS_REQUIRED_SCOPES.getOrDefault(aliasService, List.of());
            boolean hasScopes = hasRequiredScopes(originToken, requiredScopes);

            Map<String, Object> entry = new HashMap<>();
            entry.put("service", aliasService);
            entry.put("connected", hasScopes);
            entry.put("expiresAt", originToken.getExpiresAt() != null ? originToken.getExpiresAt().toString() : "");
            entry.put("aliasOf", originService);
            entry.put("disconnectable", false);

            if (!hasScopes) {
                entry.put("reason", "OAUTH_SCOPE_INSUFFICIENT");
            }

            result.add(entry);
        }

        return result;
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
        String tokenLookupService = resolveTokenLookupService(service);
        OAuthToken token = oauthTokenRepository.findByUserIdAndService(userId, tokenLookupService)
                .orElseThrow(() -> new BusinessException(ErrorCode.OAUTH_NOT_CONNECTED));

        // alias 서비스인 경우 scope 검증
        if (TOKEN_SERVICE_ALIASES.containsKey(service)) {
            List<String> requiredScopes = ALIAS_REQUIRED_SCOPES.getOrDefault(service, List.of());
            if (!hasRequiredScopes(token, requiredScopes)) {
                throw new BusinessException(ErrorCode.OAUTH_SCOPE_INSUFFICIENT,
                        service + " 실행에 필요한 scope가 부족합니다. "
                                + tokenLookupService + " 서비스를 재연결해 주세요.");
            }
        }

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
        // alias 서비스는 직접 삭제 불가
        if (TOKEN_SERVICE_ALIASES.containsKey(service)) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST,
                    service + "은(는) " + TOKEN_SERVICE_ALIASES.get(service) + "의 alias입니다. "
                            + "원본 서비스 연결을 해제해 주세요.");
        }
        oauthTokenRepository.deleteByUserIdAndService(userId, service);
    }

    private boolean isTokenExpiringSoon(OAuthToken token) {
        if (token.getExpiresAt() == null) {
            return false;
        }
        return Instant.now().plusSeconds(REFRESH_THRESHOLD_SECONDS).isAfter(token.getExpiresAt());
    }

    private String resolveTokenLookupService(String service) {
        return TOKEN_SERVICE_ALIASES.getOrDefault(service, service);
    }

    private boolean hasRequiredScopes(OAuthToken token, List<String> requiredScopes) {
        if (requiredScopes.isEmpty()) {
            return true;
        }
        List<String> tokenScopes = token.getScopes();
        if (tokenScopes == null || tokenScopes.isEmpty()) {
            return false;
        }
        return tokenScopes.containsAll(requiredScopes);
    }
}
