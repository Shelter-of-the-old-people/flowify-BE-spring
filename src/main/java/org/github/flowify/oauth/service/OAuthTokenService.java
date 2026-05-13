package org.github.flowify.oauth.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.github.flowify.common.exception.BusinessException;
import org.github.flowify.common.exception.ErrorCode;
import org.github.flowify.oauth.dto.OAuthTokenSummaryResponse;
import org.github.flowify.oauth.dto.TokenRefreshResult;
import org.github.flowify.oauth.entity.OAuthToken;
import org.github.flowify.oauth.repository.OAuthTokenRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class OAuthTokenService {

    private static final long REFRESH_THRESHOLD_SECONDS = 300;
    private static final String CONNECTION_METHOD_ALIAS = "alias";
    private static final String CONNECTION_METHOD_MANUAL_TOKEN = "manual_token";
    private static final String CONNECTION_METHOD_OAUTH_REDIRECT = "oauth_redirect";
    private static final String VALIDATION_STATUS_SCOPE_INSUFFICIENT = "scope_insufficient";
    private static final String VALIDATION_STATUS_VALID = "valid";

    private static final Map<String, String> TOKEN_SERVICE_ALIASES = Map.of(
            "google_sheets", "google_drive"
    );
    private static final Map<String, List<String>> ALIAS_REQUIRED_SCOPES = Map.of(
            "google_sheets", List.of("https://www.googleapis.com/auth/spreadsheets")
    );
    private static final Set<String> MANUAL_TOKEN_SERVICES = Set.of(
            "notion",
            "github",
            "canvas_lms"
    );

    private final OAuthTokenRepository oauthTokenRepository;
    private final TokenEncryptionService tokenEncryptionService;
    private final List<OAuthTokenRefresher> tokenRefreshers;
    private final List<ManualTokenServiceHandler> manualTokenServiceHandlers;

    private Map<String, OAuthTokenRefresher> refresherMap;
    private Map<String, ManualTokenServiceHandler> manualTokenServiceHandlerMap;

    @jakarta.annotation.PostConstruct
    private void initServiceMaps() {
        refresherMap = tokenRefreshers == null ? Collections.emptyMap() : tokenRefreshers.stream()
                .collect(Collectors.toMap(OAuthTokenRefresher::getServiceName, Function.identity()));
        manualTokenServiceHandlerMap = manualTokenServiceHandlers == null
                ? Collections.emptyMap()
                : manualTokenServiceHandlers.stream()
                .collect(Collectors.toMap(ManualTokenServiceHandler::getServiceName, Function.identity()));
    }

    public List<OAuthTokenSummaryResponse> getConnectedServices(String userId) {
        List<OAuthToken> tokens = oauthTokenRepository.findByUserId(userId);
        List<OAuthTokenSummaryResponse> result = new ArrayList<>();

        for (OAuthToken token : tokens) {
            result.add(toSummary(token));
        }

        for (Map.Entry<String, String> alias : TOKEN_SERVICE_ALIASES.entrySet()) {
            String aliasService = alias.getKey();
            String originService = alias.getValue();

            boolean alreadyConnected = tokens.stream()
                    .anyMatch(t -> t.getService().equals(aliasService));
            if (alreadyConnected) {
                continue;
            }

            OAuthToken originToken = tokens.stream()
                    .filter(t -> t.getService().equals(originService))
                    .findFirst()
                    .orElse(null);
            if (originToken == null) {
                continue;
            }

            List<String> requiredScopes = ALIAS_REQUIRED_SCOPES.getOrDefault(aliasService, List.of());
            boolean hasScopes = hasRequiredScopes(originToken, requiredScopes);
            result.add(toAliasSummary(aliasService, originService, originToken, hasScopes));
        }

        return result;
    }

    public OAuthTokenSummaryResponse upsertManualToken(String userId, String service, String accessToken) {
        if (!isManualTokenService(service)) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST,
                    service + " 서비스는 manual token 저장을 지원하지 않습니다.");
        }

        String normalizedToken = accessToken == null ? "" : accessToken.trim();
        if (normalizedToken.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Access token은 비워둘 수 없습니다.");
        }

        ManualTokenValidationResult validationResult = getManualTokenHandler(service).validate(normalizedToken);

        OAuthToken oauthToken = oauthTokenRepository.findByUserIdAndService(userId, service)
                .orElse(OAuthToken.builder()
                        .userId(userId)
                        .service(service)
                        .build());

        oauthToken.setAccessToken(tokenEncryptionService.encrypt(normalizedToken));
        oauthToken.setRefreshToken(null);
        oauthToken.setExpiresAt(validationResult.expiresAt());
        oauthToken.setScopes(validationResult.scopes() == null ? List.of() : List.copyOf(validationResult.scopes()));
        oauthToken.setConnectionMethod(CONNECTION_METHOD_MANUAL_TOKEN);
        oauthToken.setAccountEmail(blankToNull(validationResult.accountEmail()));
        oauthToken.setAccountLabel(blankToNull(validationResult.accountLabel()));
        oauthToken.setMaskedHint(buildMaskedHint(normalizedToken));
        oauthToken.setValidationStatus(VALIDATION_STATUS_VALID);
        oauthToken.setLastValidatedAt(Instant.now());

        OAuthToken savedToken = oauthTokenRepository.save(oauthToken);
        return toSummary(savedToken);
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
        oauthToken.setConnectionMethod(CONNECTION_METHOD_OAUTH_REDIRECT);
        oauthToken.setMaskedHint(buildMaskedHint(accessToken));
        oauthToken.setValidationStatus(VALIDATION_STATUS_VALID);
        oauthToken.setLastValidatedAt(Instant.now());

        oauthTokenRepository.save(oauthToken);
    }

    public String getDecryptedToken(String userId, String service) {
        return getDecryptedToken(userId, service, List.of());
    }

    public String getDecryptedToken(String userId, String service, Collection<String> requiredScopes) {
        String tokenLookupService = resolveTokenLookupService(service);
        OAuthToken token = oauthTokenRepository.findByUserIdAndService(userId, tokenLookupService)
                .orElseThrow(() -> new BusinessException(ErrorCode.OAUTH_NOT_CONNECTED));

        List<String> scopesToCheck = resolveScopesToCheck(service, requiredScopes);
        if (!hasRequiredScopes(token, scopesToCheck)) {
            throw new BusinessException(ErrorCode.OAUTH_SCOPE_INSUFFICIENT,
                    service + " 실행에 필요한 권한이 부족합니다. "
                            + tokenLookupService + " 서비스를 다시 연결해 주세요.");
        }

        if (isTokenExpiringSoon(token)) {
            refreshTokenIfNeeded(token);
        }

        return tokenEncryptionService.decrypt(token.getAccessToken());
    }

    public void validateTokenForStatusCheck(String userId, String service, Collection<String> requiredScopes) {
        String tokenLookupService = resolveTokenLookupService(service);
        OAuthToken token = oauthTokenRepository.findByUserIdAndService(userId, tokenLookupService)
                .orElseThrow(() -> new BusinessException(ErrorCode.OAUTH_NOT_CONNECTED));

        List<String> scopesToCheck = resolveScopesToCheck(service, requiredScopes);
        if (!hasRequiredScopes(token, scopesToCheck)) {
            throw new BusinessException(ErrorCode.OAUTH_SCOPE_INSUFFICIENT,
                    service + " 실행에 필요한 권한이 부족합니다. "
                            + tokenLookupService + " 서비스를 다시 연결해 주세요.");
        }

        if (isTokenExpiringSoon(token) && token.getRefreshToken() == null) {
            throw new BusinessException(ErrorCode.OAUTH_TOKEN_EXPIRED,
                    "Refresh token이 없어 토큰 상태를 복구할 수 없습니다. 서비스를 다시 연결해 주세요.");
        }
    }

    public void refreshTokenIfNeeded(OAuthToken token) {
        if (token.getRefreshToken() == null) {
            throw new BusinessException(ErrorCode.OAUTH_TOKEN_EXPIRED,
                    "Refresh token이 없습니다. 서비스를 다시 연결해 주세요.");
        }

        if (refresherMap == null || manualTokenServiceHandlerMap == null) {
            initServiceMaps();
        }

        OAuthTokenRefresher refresher = refresherMap.get(token.getService());
        if (refresher == null) {
            log.warn("Token refresh is not supported for service={}", token.getService());
            throw new BusinessException(ErrorCode.OAUTH_TOKEN_EXPIRED,
                    token.getService() + " 서비스는 토큰 자동 갱신을 지원하지 않습니다. 다시 연결해 주세요.");
        }

        String decryptedRefreshToken = tokenEncryptionService.decrypt(token.getRefreshToken());
        TokenRefreshResult result = refresher.refresh(decryptedRefreshToken);

        token.setAccessToken(tokenEncryptionService.encrypt(result.getAccessToken()));
        token.setExpiresAt(Instant.now().plusSeconds(result.getExpiresIn() != null ? result.getExpiresIn() : 3600));
        if (result.getRefreshToken() != null) {
            token.setRefreshToken(tokenEncryptionService.encrypt(result.getRefreshToken()));
        }
        token.setMaskedHint(buildMaskedHint(result.getAccessToken()));
        token.setValidationStatus(VALIDATION_STATUS_VALID);
        token.setLastValidatedAt(Instant.now());
        oauthTokenRepository.save(token);

        log.info("Token refreshed: userId={}, service={}", token.getUserId(), token.getService());
    }

    public void deleteToken(String userId, String service) {
        if (TOKEN_SERVICE_ALIASES.containsKey(service)) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST,
                    service + "는 " + TOKEN_SERVICE_ALIASES.get(service) + "의 alias입니다. "
                            + "원본 서비스를 해제해 주세요.");
        }
        oauthTokenRepository.deleteByUserIdAndService(userId, service);
    }

    public boolean isManualTokenService(String service) {
        return MANUAL_TOKEN_SERVICES.contains(service);
    }

    private OAuthTokenSummaryResponse toSummary(OAuthToken token) {
        return OAuthTokenSummaryResponse.builder()
                .service(token.getService())
                .connected(true)
                .connectionMethod(resolveConnectionMethod(token))
                .accountEmail(blankToNull(token.getAccountEmail()))
                .accountLabel(blankToNull(token.getAccountLabel()))
                .expiresAt(toIsoString(token.getExpiresAt()))
                .aliasOf(null)
                .disconnectable(true)
                .reason(null)
                .maskedHint(blankToNull(token.getMaskedHint()))
                .updatedAt(toIsoString(token.getUpdatedAt()))
                .validationStatus(resolveValidationStatus(token))
                .build();
    }

    private OAuthTokenSummaryResponse toAliasSummary(
            String aliasService,
            String originService,
            OAuthToken originToken,
            boolean hasScopes
    ) {
        return OAuthTokenSummaryResponse.builder()
                .service(aliasService)
                .connected(hasScopes)
                .connectionMethod(CONNECTION_METHOD_ALIAS)
                .accountEmail(blankToNull(originToken.getAccountEmail()))
                .accountLabel(blankToNull(originToken.getAccountLabel()))
                .expiresAt(toIsoString(originToken.getExpiresAt()))
                .aliasOf(originService)
                .disconnectable(false)
                .reason(hasScopes ? null : ErrorCode.OAUTH_SCOPE_INSUFFICIENT.name())
                .maskedHint(blankToNull(originToken.getMaskedHint()))
                .updatedAt(toIsoString(originToken.getUpdatedAt()))
                .validationStatus(hasScopes
                        ? resolveValidationStatus(originToken)
                        : VALIDATION_STATUS_SCOPE_INSUFFICIENT)
                .build();
    }

    private String resolveConnectionMethod(OAuthToken token) {
        if (token.getConnectionMethod() != null && !token.getConnectionMethod().isBlank()) {
            return token.getConnectionMethod();
        }
        if (isManualTokenService(token.getService())) {
            return CONNECTION_METHOD_MANUAL_TOKEN;
        }
        return CONNECTION_METHOD_OAUTH_REDIRECT;
    }

    private String resolveValidationStatus(OAuthToken token) {
        if (token.getValidationStatus() != null && !token.getValidationStatus().isBlank()) {
            return token.getValidationStatus();
        }
        return VALIDATION_STATUS_VALID;
    }

    private String toIsoString(Instant value) {
        return value == null ? null : value.toString();
    }

    private String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value;
    }

    private String buildMaskedHint(String accessToken) {
        if (accessToken == null || accessToken.isBlank()) {
            return null;
        }
        String normalized = accessToken.trim();
        String suffix = normalized.substring(Math.max(0, normalized.length() - 4));
        return "****" + suffix;
    }

    private ManualTokenServiceHandler getManualTokenHandler(String service) {
        if (manualTokenServiceHandlerMap == null || refresherMap == null) {
            initServiceMaps();
        }

        ManualTokenServiceHandler handler = manualTokenServiceHandlerMap.get(service);
        if (handler == null) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST,
                    service + " 서비스는 manual token 저장을 지원하지 않습니다.");
        }
        return handler;
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

    private List<String> resolveScopesToCheck(String service, Collection<String> requiredScopes) {
        List<String> scopesToCheck = requiredScopes == null || requiredScopes.isEmpty()
                ? ALIAS_REQUIRED_SCOPES.getOrDefault(service, List.of())
                : List.copyOf(requiredScopes);

        if (TOKEN_SERVICE_ALIASES.containsKey(service)) {
            scopesToCheck = mergeScopes(scopesToCheck,
                    ALIAS_REQUIRED_SCOPES.getOrDefault(service, List.of()));
        }

        return scopesToCheck;
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

    private List<String> mergeScopes(List<String> first, List<String> second) {
        return java.util.stream.Stream.concat(first.stream(), second.stream())
                .distinct()
                .toList();
    }
}