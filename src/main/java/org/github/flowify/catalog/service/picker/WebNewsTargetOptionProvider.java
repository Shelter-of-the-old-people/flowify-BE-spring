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
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class WebNewsTargetOptionProvider implements TargetOptionProvider {

    private static final String SERVICE_KEY = "web_news";
    private static final String SEBOARD_POSTS_MODE = "seboard_posts";
    private static final String SEBOARD_NEW_POSTS_MODE = "seboard_new_posts";

    @Qualifier("webNewsWebClient")
    private final WebClient webNewsWebClient;

    @Override
    public String getServiceKey() {
        return SERVICE_KEY;
    }

    @Override
    public TargetOptionResponse getOptions(
            String sourceMode, String token, String parentId, String query, String cursor) {
        if (SEBOARD_POSTS_MODE.equals(sourceMode)
                || SEBOARD_NEW_POSTS_MODE.equals(sourceMode)) {
            return listSeBoardCategories(query);
        }

        throw new BusinessException(ErrorCode.INVALID_REQUEST,
                "Web news source mode does not support target options: " + sourceMode);
    }

    @SuppressWarnings("unchecked")
    private TargetOptionResponse listSeBoardCategories(String query) {
        try {
            List<Map<String, Object>> menus = webNewsWebClient.get()
                    .uri("/menu")
                    .retrieve()
                    .bodyToMono(List.class)
                    .timeout(Duration.ofSeconds(30))
                    .blockOptional()
                    .orElse(List.of());

            List<TargetOptionItem> items = new ArrayList<>();
            collectCategories(menus, null, null, items);

            return TargetOptionResponse.builder()
                    .items(items.stream()
                            .filter(item -> matchesQuery(item, query))
                            .toList())
                    .nextCursor(null)
                    .build();
        } catch (WebClientResponseException e) {
            log.error("SE Board menu API error: status={}, body={}",
                    e.getStatusCode().value(), e.getResponseBodyAsString());
            if (e.getStatusCode().value() == 429) {
                throw new BusinessException(ErrorCode.EXTERNAL_RATE_LIMITED,
                        "SE Board request limit exceeded. Please try again later.");
            }
            throw new BusinessException(ErrorCode.EXTERNAL_API_ERROR,
                    "SE Board menu lookup failed: " + e.getStatusCode().value());
        } catch (RuntimeException e) {
            log.error("SE Board menu API request failed", e);
            throw new BusinessException(ErrorCode.EXTERNAL_API_ERROR,
                    "SE Board menu lookup failed.");
        }
    }

    @SuppressWarnings("unchecked")
    private void collectCategories(
            List<Map<String, Object>> menus,
            String parentId,
            String parentName,
            List<TargetOptionItem> items
    ) {
        for (Map<String, Object> menu : menus) {
            String menuId = asString(menu.get("menuId"));
            String name = asString(menu.get("name"));
            String type = asString(menu.get("type"));

            if ("CATEGORY".equals(type) && isAccessible(menu)) {
                items.add(toTargetOption(menu, parentId, parentName));
            }

            if (menu.get("subMenu") instanceof List<?> rawSubMenus) {
                collectCategories((List<Map<String, Object>>) rawSubMenus, menuId, name, items);
            }
        }
    }

    private TargetOptionItem toTargetOption(
            Map<String, Object> category,
            String parentId,
            String parentName
    ) {
        String categoryId = asString(category.get("menuId"));
        String categoryName = asString(category.get("name"));
        String displayPath = buildDisplayPath(parentName, categoryName);
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("provider", "seboard");
        putIfPresent(metadata, "boardId", parentId);
        putIfPresent(metadata, "boardName", parentName);
        putIfPresent(metadata, "categoryId", categoryId);
        putIfPresent(metadata, "categoryName", categoryName);
        putIfPresent(metadata, "displayPath", displayPath);
        putIfPresent(metadata, "urlId", category.get("urlId"));
        putIfPresent(metadata, "accessible", category.get("accessible"));

        return TargetOptionItem.builder()
                .id(categoryId)
                .label(displayPath)
                .description(parentName == null ? "SE Board category" : parentName)
                .type("category")
                .metadata(metadata)
                .build();
    }

    private String buildDisplayPath(String parentName, String categoryName) {
        if (parentName == null || parentName.isBlank()) {
            return categoryName;
        }

        if (categoryName == null || categoryName.isBlank()) {
            return parentName;
        }

        return parentName + " > " + categoryName;
    }

    private boolean isAccessible(Map<String, Object> menu) {
        return !(menu.get("accessible") instanceof Boolean accessible) || accessible;
    }

    private boolean matchesQuery(TargetOptionItem item, String query) {
        if (query == null || query.isBlank()) {
            return true;
        }
        String normalizedQuery = query.toLowerCase(Locale.ROOT);
        return containsIgnoreCase(item.getLabel(), normalizedQuery)
                || containsIgnoreCase(item.getDescription(), normalizedQuery);
    }

    private boolean containsIgnoreCase(String value, String normalizedQuery) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(normalizedQuery);
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
