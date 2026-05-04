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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class NotionSinkTargetOptionProvider implements SinkTargetOptionProvider {

    private static final String SERVICE_KEY = "notion";
    private static final String SUPPORTED_TYPE = "page";

    @Qualifier("notionWebClient")
    private final WebClient notionWebClient;

    @Override
    public String getServiceKey() {
        return SERVICE_KEY;
    }

    @Override
    @SuppressWarnings("unchecked")
    public TargetOptionResponse getOptions(String token, String type, String parentId, String query, String cursor) {
        if (!SUPPORTED_TYPE.equals(type)) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST,
                    "Notion sink picker는 page type만 지원합니다: " + type);
        }

        Map<String, Object> requestBody = new LinkedHashMap<>();
        if (query != null && !query.isBlank()) {
            requestBody.put("query", query);
        }
        requestBody.put("filter", Map.of("property", "object", "value", "page"));
        requestBody.put("sort", Map.of("timestamp", "last_edited_time", "direction", "descending"));
        requestBody.put("page_size", 20);
        if (cursor != null && !cursor.isBlank()) {
            requestBody.put("start_cursor", cursor);
        }

        try {
            Map<String, Object> response = notionWebClient.post()
                    .uri("/search")
                    .headers(headers -> headers.setBearerAuth(token))
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .timeout(Duration.ofSeconds(30))
                    .blockOptional()
                    .orElse(Map.of());

            List<Map<String, Object>> results = response.get("results") instanceof List<?> rawResults
                    ? (List<Map<String, Object>>) rawResults
                    : List.of();

            List<TargetOptionItem> items = new ArrayList<>();
            for (Map<String, Object> result : results) {
                String id = asString(result.get("id"));
                if (id == null || id.isBlank()) {
                    continue;
                }
                items.add(TargetOptionItem.builder()
                        .id(id.replace("-", ""))
                        .label(extractTitle(result))
                        .description("Notion page")
                        .type(SUPPORTED_TYPE)
                        .metadata(buildMetadata(result))
                        .build());
            }

            return TargetOptionResponse.builder()
                    .items(items)
                    .nextCursor(asBoolean(response.get("has_more")) ? asString(response.get("next_cursor")) : null)
                    .build();
        } catch (WebClientResponseException e) {
            log.error("Notion page search error: status={}, body={}",
                    e.getStatusCode().value(), e.getResponseBodyAsString());

            if (e.getStatusCode().value() == 401) {
                throw new BusinessException(ErrorCode.OAUTH_TOKEN_EXPIRED,
                        "Notion 토큰이 만료되었거나 유효하지 않습니다. 다시 연결이 필요합니다.");
            }
            if (e.getStatusCode().value() == 403) {
                throw new BusinessException(ErrorCode.OAUTH_SCOPE_INSUFFICIENT,
                        "Notion 페이지 조회 권한이 부족합니다. 공유 범위를 다시 확인해주세요.");
            }
            throw new BusinessException(ErrorCode.EXTERNAL_API_ERROR,
                    "Notion 페이지 목록 조회에 실패했습니다: " + e.getStatusCode().value());
        }
    }

    @SuppressWarnings("unchecked")
    private String extractTitle(Map<String, Object> result) {
        Object propertiesObject = result.get("properties");
        if (propertiesObject instanceof Map<?, ?> properties) {
            for (Object value : properties.values()) {
                if (!(value instanceof Map<?, ?> property)) {
                    continue;
                }
                if (!"title".equals(asString(property.get("type")))) {
                    continue;
                }
                Object titleObject = property.get("title");
                if (titleObject instanceof List<?> titleItems) {
                    String title = titleItems.stream()
                            .filter(Map.class::isInstance)
                            .map(Map.class::cast)
                            .map(item -> asString(item.get("plain_text")))
                            .filter(text -> text != null && !text.isBlank())
                            .reduce("", String::concat)
                            .trim();
                    if (!title.isBlank()) {
                        return title;
                    }
                }
            }
        }
        return "Untitled";
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> buildMetadata(Map<String, Object> result) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        putIfPresent(metadata, "lastEditedTime", result.get("last_edited_time"));
        putIfPresent(metadata, "url", result.get("url"));

        Object parentObject = result.get("parent");
        if (parentObject instanceof Map<?, ?> parent) {
            putIfPresent(metadata, "parentType", parent.get("type"));
            putIfPresent(metadata, "parentId", parent.get("page_id"));
        }

        return metadata;
    }

    private void putIfPresent(Map<String, Object> metadata, String key, Object value) {
        if (value != null) {
            metadata.put(key, value);
        }
    }

    private boolean asBoolean(Object value) {
        return Boolean.TRUE.equals(value);
    }

    private String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
