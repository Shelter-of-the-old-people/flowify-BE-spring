package org.github.flowify.oauth.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.github.flowify.common.exception.BusinessException;
import org.github.flowify.common.exception.ErrorCode;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Duration;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class NotionManualTokenHandler implements ManualTokenServiceHandler {

    @Qualifier("notionWebClient")
    private final WebClient notionWebClient;

    @Override
    public String getServiceName() {
        return "notion";
    }

    @Override
    @SuppressWarnings("unchecked")
    public ManualTokenValidationResult validate(String accessToken) {
        try {
            Map<String, Object> response = notionWebClient.get()
                    .uri("/users/me")
                    .headers(headers -> headers.setBearerAuth(accessToken))
                    .retrieve()
                    .bodyToMono(Map.class)
                    .timeout(Duration.ofSeconds(30))
                    .blockOptional()
                    .orElse(Map.of());

            String accountEmail = firstNonBlank(
                    getNestedString(response, "bot", "owner", "user", "person", "email"),
                    getNestedString(response, "owner", "user", "person", "email")
            );
            String accountLabel = firstNonBlank(
                    asString(response.get("workspace_name")),
                    getNestedString(response, "owner", "workspace_name"),
                    asString(response.get("name")),
                    getNestedString(response, "owner", "user", "name"),
                    "Notion integration"
            );

            return new ManualTokenValidationResult(accountEmail, accountLabel, null, List.of());
        } catch (WebClientResponseException e) {
            log.warn("Notion token validation failed: status={}, body={}",
                    e.getStatusCode().value(), e.getResponseBodyAsString());

            if (e.getStatusCode().value() == 401) {
                throw new BusinessException(ErrorCode.OAUTH_TOKEN_INVALID,
                        "Notion 토큰이 유효하지 않습니다. 새 토큰을 다시 입력해 주세요.");
            }
            if (e.getStatusCode().value() == 403) {
                throw new BusinessException(ErrorCode.OAUTH_SCOPE_INSUFFICIENT,
                        "Notion 연결 권한이 부족합니다. 연결 권한과 페이지 공유 범위를 다시 확인해 주세요.");
            }
            throw new BusinessException(ErrorCode.EXTERNAL_API_ERROR,
                    "Notion 토큰 검증 중 오류가 발생했습니다. 잠시 후 다시 시도해 주세요.");
        }
    }

    @SuppressWarnings("unchecked")
    private String getNestedString(Map<String, Object> source, String... keys) {
        Object current = source;
        for (String key : keys) {
            if (!(current instanceof Map<?, ?> map)) {
                return null;
            }
            current = ((Map<String, Object>) map).get(key);
        }
        return asString(current);
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