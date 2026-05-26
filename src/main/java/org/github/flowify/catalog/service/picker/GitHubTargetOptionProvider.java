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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class GitHubTargetOptionProvider implements TargetOptionProvider {

    private static final String SERVICE_KEY = "github";
    private static final int PAGE_SIZE = 50;

    @Qualifier("githubWebClient")
    private final WebClient githubWebClient;

    @Override
    public String getServiceKey() {
        return SERVICE_KEY;
    }

    @Override
    @SuppressWarnings("unchecked")
    public TargetOptionResponse getOptions(
            String sourceMode,
            String token,
            String parentId,
            String query,
            String cursor
    ) {
        if (!"new_pr".equals(sourceMode)) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST,
                    "GitHub source mode target option을 지원하지 않습니다: " + sourceMode);
        }

        int page = parsePage(cursor);
        try {
            List<Map<String, Object>> repositories = githubWebClient.get()
                    .uri(uriBuilder -> uriBuilder.path("/user/repos")
                            .queryParam("sort", "updated")
                            .queryParam("direction", "desc")
                            .queryParam("per_page", PAGE_SIZE)
                            .queryParam("page", page)
                            .build())
                    .headers(headers -> headers.setBearerAuth(token))
                    .retrieve()
                    .bodyToMono(List.class)
                    .timeout(Duration.ofSeconds(30))
                    .blockOptional()
                    .orElse(List.of());

            List<TargetOptionItem> items = repositories.stream()
                    .filter(repository -> matchesQuery(repository, query))
                    .map(this::toTargetOption)
                    .toList();

            String nextCursor = repositories.size() < PAGE_SIZE ? null : String.valueOf(page + 1);
            return TargetOptionResponse.builder()
                    .items(items)
                    .nextCursor(nextCursor)
                    .build();
        } catch (WebClientResponseException e) {
            log.error("GitHub repo option API error: status={}, body={}",
                    e.getStatusCode().value(), e.getResponseBodyAsString());
            if (e.getStatusCode().value() == 401) {
                throw new BusinessException(ErrorCode.OAUTH_TOKEN_EXPIRED,
                        "GitHub 토큰이 만료되었거나 유효하지 않습니다. 다시 연결해 주세요.");
            }
            if (e.getStatusCode().value() == 403) {
                throw new BusinessException(ErrorCode.OAUTH_SCOPE_INSUFFICIENT,
                        "GitHub 저장소 목록을 조회할 권한이 부족합니다. repo 권한이 포함된 토큰을 확인해 주세요.");
            }
            if (e.getStatusCode().value() == 429) {
                throw new BusinessException(ErrorCode.EXTERNAL_RATE_LIMITED,
                        "GitHub API 요청 제한에 도달했습니다. 잠시 후 다시 시도해 주세요.");
            }
            throw new BusinessException(ErrorCode.EXTERNAL_API_ERROR,
                    "GitHub 저장소 목록 조회에 실패했습니다: " + e.getStatusCode().value());
        }
    }

    private int parsePage(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return 1;
        }
        try {
            int page = Integer.parseInt(cursor.trim());
            return page >= 1 ? page : 1;
        } catch (NumberFormatException ignored) {
            return 1;
        }
    }

    private boolean matchesQuery(Map<String, Object> repository, String query) {
        if (query == null || query.isBlank()) {
            return true;
        }

        String normalizedQuery = query.trim().toLowerCase(Locale.ROOT);
        return matchesText(repository.get("full_name"), normalizedQuery)
                || matchesText(repository.get("name"), normalizedQuery)
                || matchesText(repository.get("description"), normalizedQuery)
                || matchesOwner(repository.get("owner"), normalizedQuery);
    }

    @SuppressWarnings("unchecked")
    private boolean matchesOwner(Object owner, String normalizedQuery) {
        if (!(owner instanceof Map<?, ?> ownerMap)) {
            return false;
        }
        return matchesText(((Map<String, Object>) ownerMap).get("login"), normalizedQuery);
    }

    private boolean matchesText(Object value, String normalizedQuery) {
        if (value == null) {
            return false;
        }
        return String.valueOf(value).toLowerCase(Locale.ROOT).contains(normalizedQuery);
    }

    @SuppressWarnings("unchecked")
    private TargetOptionItem toTargetOption(Map<String, Object> repository) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        String fullName = asString(repository.get("full_name"));
        Map<String, Object> owner = repository.get("owner") instanceof Map<?, ?> ownerMap
                ? (Map<String, Object>) ownerMap
                : Map.of();

        putIfPresent(metadata, "owner", owner.get("login"));
        putIfPresent(metadata, "repo", repository.get("name"));
        putIfPresent(metadata, "defaultBranch", repository.get("default_branch"));
        putIfPresent(metadata, "visibility", repository.get("visibility"));
        putIfPresent(metadata, "ownerType", owner.get("type"));
        putIfPresent(metadata, "htmlUrl", repository.get("html_url"));
        metadata.put("private", Boolean.TRUE.equals(repository.get("private")));

        String description = buildDescription(repository, owner);
        return TargetOptionItem.builder()
                .id(fullName)
                .label(fullName)
                .description(description)
                .type("repository")
                .metadata(metadata)
                .build();
    }

    private String buildDescription(Map<String, Object> repository, Map<String, Object> owner) {
        String visibility = asString(repository.get("visibility"));
        if (visibility == null) {
            visibility = Boolean.TRUE.equals(repository.get("private")) ? "private" : "public";
        }
        String ownerType = asString(owner.get("type"));
        String defaultBranch = asString(repository.get("default_branch"));

        StringBuilder description = new StringBuilder();
        description.append(visibility != null ? visibility : "repository");
        if (ownerType != null) {
            description.append(" · ").append(ownerType);
        }
        if (defaultBranch != null) {
            description.append(" · default ").append(defaultBranch);
        }
        return description.toString();
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
