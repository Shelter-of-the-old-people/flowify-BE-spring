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
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class SlackSinkTargetOptionProvider implements SinkTargetOptionProvider {

    private static final String SERVICE_KEY = "slack";
    private static final String SUPPORTED_TYPE = "channel";

    @Qualifier("slackWebClient")
    private final WebClient slackWebClient;

    @Override
    public String getServiceKey() {
        return SERVICE_KEY;
    }

    @Override
    @SuppressWarnings("unchecked")
    public TargetOptionResponse getOptions(String token, String type, String parentId, String query, String cursor) {
        if (!SUPPORTED_TYPE.equals(type)) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST,
                    "Slack sink picker는 channel type만 지원합니다: " + type);
        }

        try {
            Map<String, Object> response = slackWebClient.get()
                    .uri(uriBuilder -> {
                        var builder = uriBuilder
                                .path("/conversations.list")
                                .queryParam("types", "public_channel")
                                .queryParam("exclude_archived", true)
                                .queryParam("limit", 100);
                        if (cursor != null && !cursor.isBlank()) {
                            builder.queryParam("cursor", cursor);
                        }
                        return builder.build();
                    })
                    .headers(headers -> headers.setBearerAuth(token))
                    .retrieve()
                    .bodyToMono(Map.class)
                    .timeout(Duration.ofSeconds(30))
                    .blockOptional()
                    .orElse(Map.of());

            if (!Boolean.TRUE.equals(response.get("ok"))) {
                throw new BusinessException(
                        ErrorCode.EXTERNAL_API_ERROR,
                        "Slack 채널 목록 조회에 실패했습니다: " + response.getOrDefault("error", "unknown")
                );
            }

            List<Map<String, Object>> channels = response.get("channels") instanceof List<?> rawChannels
                    ? (List<Map<String, Object>>) rawChannels
                    : List.of();

            List<TargetOptionItem> items = channels.stream()
                    .filter(channel -> containsIgnoreCase(asString(channel.get("name")), query))
                    .map(channel -> TargetOptionItem.builder()
                            .id(asString(channel.get("id")))
                            .label(asString(channel.get("name")))
                            .description("공개 채널")
                            .type(SUPPORTED_TYPE)
                            .metadata(Map.of(
                                    "isPrivate", false,
                                    "isMember", Boolean.TRUE.equals(channel.get("is_member")),
                                    "memberCount", channel.getOrDefault("num_members", 0)
                            ))
                            .build())
                    .toList();

            String nextCursor = null;
            if (response.get("response_metadata") instanceof Map<?, ?> metadata) {
                nextCursor = asString(metadata.get("next_cursor"));
            }

            return TargetOptionResponse.builder()
                    .items(items)
                    .nextCursor(nextCursor == null || nextCursor.isBlank() ? null : nextCursor)
                    .build();
        } catch (WebClientResponseException e) {
            log.error("Slack channel list error: status={}, body={}",
                    e.getStatusCode().value(), e.getResponseBodyAsString());

            if (e.getStatusCode().value() == 401) {
                throw new BusinessException(ErrorCode.OAUTH_TOKEN_EXPIRED,
                        "Slack 토큰이 만료되었습니다. 재연결이 필요합니다.");
            }
            if (e.getStatusCode().value() == 403) {
                throw new BusinessException(ErrorCode.OAUTH_SCOPE_INSUFFICIENT,
                        "Slack 채널 조회 권한이 부족합니다. 범위를 다시 확인해주세요.");
            }
            throw new BusinessException(ErrorCode.EXTERNAL_API_ERROR,
                    "Slack 채널 목록 조회에 실패했습니다: " + e.getStatusCode().value());
        }
    }

    private boolean containsIgnoreCase(String value, String query) {
        if (query == null || query.isBlank()) {
            return true;
        }
        if (value == null) {
            return false;
        }
        return value.toLowerCase(Locale.ROOT).contains(query.toLowerCase(Locale.ROOT));
    }

    private String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
