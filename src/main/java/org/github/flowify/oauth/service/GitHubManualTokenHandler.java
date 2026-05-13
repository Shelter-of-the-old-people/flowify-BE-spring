package org.github.flowify.oauth.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.github.flowify.common.exception.BusinessException;
import org.github.flowify.common.exception.ErrorCode;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class GitHubManualTokenHandler implements ManualTokenServiceHandler {

    @Qualifier("githubWebClient")
    private final WebClient githubWebClient;

    @Override
    public String getServiceName() {
        return "github";
    }

    @Override
    @SuppressWarnings("unchecked")
    public ManualTokenValidationResult validate(String accessToken) {
        try {
            ResponseEntity<Map> response = githubWebClient.get()
                    .uri("/user")
                    .headers(headers -> headers.setBearerAuth(accessToken))
                    .retrieve()
                    .toEntity(Map.class)
                    .timeout(Duration.ofSeconds(30))
                    .blockOptional()
                    .orElse(ResponseEntity.ok(Map.of()));

            Map<String, Object> body = response.getBody() == null
                    ? Map.of()
                    : (Map<String, Object>) response.getBody();
            List<String> scopes = parseScopes(response.getHeaders().getFirst("X-OAuth-Scopes"));

            if (!scopes.isEmpty() && scopes.stream().noneMatch(this::supportsRepositoryAccess)) {
                throw new BusinessException(ErrorCode.OAUTH_SCOPE_INSUFFICIENT,
                        "GitHub 저장소 접근 권한이 부족합니다. repo 권한이 포함된 토큰을 입력해 주세요.");
            }

            String accountEmail = firstNonBlank(asString(body.get("email")));
            String accountLabel = firstNonBlank(
                    asString(body.get("login")),
                    asString(body.get("name")),
                    accountEmail,
                    "GitHub user"
            );

            return new ManualTokenValidationResult(accountEmail, accountLabel, null, scopes);
        } catch (WebClientResponseException e) {
            log.warn("GitHub token validation failed: status={}, body={}",
                    e.getStatusCode().value(), e.getResponseBodyAsString());

            if (e.getStatusCode().value() == 401) {
                throw new BusinessException(ErrorCode.OAUTH_TOKEN_INVALID,
                        "GitHub 토큰이 유효하지 않습니다. 새 토큰을 다시 입력해 주세요.");
            }
            if (e.getStatusCode().value() == 403) {
                throw new BusinessException(ErrorCode.OAUTH_SCOPE_INSUFFICIENT,
                        "GitHub 접근 권한이 부족합니다. repo 권한을 다시 확인해 주세요.");
            }
            throw new BusinessException(ErrorCode.EXTERNAL_API_ERROR,
                    "GitHub 토큰 검증 중 오류가 발생했습니다. 잠시 후 다시 시도해 주세요.");
        }
    }

    private List<String> parseScopes(String rawScopes) {
        if (rawScopes == null || rawScopes.isBlank()) {
            return List.of();
        }
        return Arrays.stream(rawScopes.split(","))
                .map(String::trim)
                .filter(scope -> !scope.isBlank())
                .toList();
    }

    private boolean supportsRepositoryAccess(String scope) {
        String normalizedScope = scope.toLowerCase(Locale.ROOT);
        return normalizedScope.equals("repo")
                || normalizedScope.equals("public_repo")
                || normalizedScope.startsWith("repo:");
    }

    private String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }
}