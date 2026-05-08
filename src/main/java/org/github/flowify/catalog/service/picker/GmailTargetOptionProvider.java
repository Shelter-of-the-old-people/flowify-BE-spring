package org.github.flowify.catalog.service.picker;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.github.flowify.catalog.dto.picker.TargetOptionItem;
import org.github.flowify.catalog.dto.picker.TargetOptionResponse;
import org.github.flowify.common.exception.BusinessException;
import org.github.flowify.common.exception.ErrorCode;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class GmailTargetOptionProvider implements TargetOptionProvider {

    private static final String SERVICE_KEY = "gmail";

    @Qualifier("gmailWebClient")
    private final WebClient gmailWebClient;

    @Override
    public String getServiceKey() {
        return SERVICE_KEY;
    }

    @Override
    public TargetOptionResponse getOptions(
            String sourceMode, String token, String parentId, String query, String cursor) {
        if ("label_emails".equals(sourceMode)) {
            return listLabels(token, query);
        }

        throw new BusinessException(ErrorCode.INVALID_REQUEST,
                "Gmail source mode는 target option을 지원하지 않습니다: " + sourceMode);
    }

    @SuppressWarnings("unchecked")
    private TargetOptionResponse listLabels(String token, String query) {
        try {
            Map<String, Object> response = gmailWebClient.get()
                    .uri("/labels")
                    .headers(headers -> headers.setBearerAuth(token))
                    .retrieve()
                    .bodyToMono(Map.class)
                    .timeout(Duration.ofSeconds(30))
                    .blockOptional()
                    .orElse(Map.of());

            List<Map<String, Object>> labels = response.get("labels") instanceof List<?> rawLabels
                    ? (List<Map<String, Object>>) rawLabels
                    : List.of();

            List<TargetOptionItem> items = labels.stream()
                    .filter(label -> matchesQuery(label, query))
                    .map(this::toTargetOption)
                    .toList();

            return TargetOptionResponse.builder()
                    .items(items)
                    .nextCursor(null)
                    .build();
        } catch (WebClientResponseException e) {
            log.error("Gmail label API error: status={}, body={}",
                    e.getStatusCode().value(), e.getResponseBodyAsString());
            if (e.getStatusCode().value() == 401) {
                throw new BusinessException(ErrorCode.OAUTH_TOKEN_EXPIRED,
                        "Gmail 토큰이 만료되었습니다. 재연결이 필요합니다.");
            }
            if (e.getStatusCode().value() == 403) {
                throw new BusinessException(ErrorCode.OAUTH_SCOPE_INSUFFICIENT,
                        "Gmail 라벨 조회 권한이 부족합니다. 서비스 재연결이 필요합니다.");
            }
            if (e.getStatusCode().value() == 429) {
                throw new BusinessException(ErrorCode.EXTERNAL_RATE_LIMITED,
                        "Gmail API 요청 제한에 도달했습니다. 잠시 후 다시 시도해 주세요.");
            }
            throw new BusinessException(ErrorCode.EXTERNAL_API_ERROR,
                    "Gmail 라벨 목록 조회에 실패했습니다: " + e.getStatusCode().value());
        }
    }

    private TargetOptionItem toTargetOption(Map<String, Object> label) {
        Map<String, Object> metadata = new HashMap<>();
        putIfPresent(metadata, "type", label.get("type"));
        putIfPresent(metadata, "messageCount", label.get("messagesTotal"));
        putIfPresent(metadata, "unreadCount", label.get("messagesUnread"));

        String name = asString(label.get("name"));
        return TargetOptionItem.builder()
                .id(asString(label.get("id")))
                .label(name)
                .description("Gmail label")
                .type("label")
                .metadata(metadata)
                .build();
    }

    private boolean matchesQuery(Map<String, Object> label, String query) {
        if (query == null || query.isBlank()) {
            return true;
        }
        String name = asString(label.get("name"));
        return name != null && name.toLowerCase().contains(query.toLowerCase());
    }

    private void putIfPresent(Map<String, Object> metadata, String key, Object value) {
        if (value != null) {
            metadata.put(key, value);
        }
    }

    private String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
