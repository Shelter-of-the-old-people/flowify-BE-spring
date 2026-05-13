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
public class CanvasLmsManualTokenHandler implements ManualTokenServiceHandler {

    @Qualifier("canvasWebClient")
    private final WebClient canvasWebClient;

    @Override
    public String getServiceName() {
        return "canvas_lms";
    }

    @Override
    @SuppressWarnings("unchecked")
    public ManualTokenValidationResult validate(String accessToken) {
        try {
            Map<String, Object> profile = canvasWebClient.get()
                    .uri("/api/v1/users/self/profile")
                    .headers(headers -> headers.setBearerAuth(accessToken))
                    .retrieve()
                    .bodyToMono(Map.class)
                    .timeout(Duration.ofSeconds(30))
                    .blockOptional()
                    .orElse(Map.of());

            List<Map<String, Object>> courses = canvasWebClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/api/v1/courses")
                            .queryParam("per_page", 1)
                            .build())
                    .headers(headers -> headers.setBearerAuth(accessToken))
                    .retrieve()
                    .bodyToMono(List.class)
                    .timeout(Duration.ofSeconds(30))
                    .blockOptional()
                    .orElse(List.of());

            String accountEmail = firstNonBlank(
                    asString(profile.get("primary_email")),
                    asString(profile.get("login_id"))
            );
            String accountLabel = firstNonBlank(
                    asString(profile.get("name")),
                    asString(profile.get("short_name")),
                    accountEmail,
                    "Canvas user"
            );
            List<String> scopes = courses.isEmpty()
                    ? List.of("courses")
                    : List.of("courses", "files");

            return new ManualTokenValidationResult(accountEmail, accountLabel, null, scopes);
        } catch (WebClientResponseException e) {
            log.warn("Canvas LMS token validation failed: status={}, body={}",
                    e.getStatusCode().value(), e.getResponseBodyAsString());

            if (e.getStatusCode().value() == 401) {
                throw new BusinessException(ErrorCode.OAUTH_TOKEN_INVALID,
                        "Canvas LMS 토큰이 유효하지 않습니다. 새 토큰을 다시 입력해 주세요.");
            }
            if (e.getStatusCode().value() == 403) {
                throw new BusinessException(ErrorCode.OAUTH_SCOPE_INSUFFICIENT,
                        "Canvas LMS 접근 권한이 부족합니다. 코스와 파일 접근 권한을 다시 확인해 주세요.");
            }
            throw new BusinessException(ErrorCode.EXTERNAL_API_ERROR,
                    "Canvas LMS 토큰 검증 중 오류가 발생했습니다. 잠시 후 다시 시도해 주세요.");
        }
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