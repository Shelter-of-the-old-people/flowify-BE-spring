package org.github.flowify.catalog.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.github.flowify.oauth.service.OAuthTokenService;
import org.github.flowify.workflow.dto.NodeStatusResponse;
import org.github.flowify.workflow.entity.NodeDefinition;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class NodeLifecycleService {

    private final CatalogService catalogService;
    private final OAuthTokenService oauthTokenService;

    public List<NodeStatusResponse> evaluateAll(List<NodeDefinition> nodes, String userId) {
        if (nodes == null) {
            return List.of();
        }
        return nodes.stream()
                .map(node -> evaluate(node, userId))
                .toList();
    }

    public NodeStatusResponse evaluate(NodeDefinition node, String userId) {
        List<String> missingFields = new ArrayList<>();
        boolean configured;
        boolean needsAuth;

        String role = node.getRole();
        if ("start".equals(role)) {
            configured = evaluateStartNode(node, missingFields);
            needsAuth = catalogService.isAuthRequired(node.getType());
        } else if ("end".equals(role)) {
            configured = evaluateEndNode(node, missingFields);
            needsAuth = catalogService.isAuthRequired(node.getType());
        } else {
            configured = evaluateMiddleNode(node, missingFields);
            needsAuth = false;
        }

        // 프론트가 명시적으로 isConfigured=false를 보낸 경우 configured를 true로 뒤집지 않음
        if (configured) {
            Map<String, Object> config = node.getConfig();
            if (config != null && config.containsKey("isConfigured")) {
                Object isConfigured = config.get("isConfigured");
                if (Boolean.FALSE.equals(isConfigured)) {
                    configured = false;
                }
            }
        }

        boolean hasToken = true;
        if (needsAuth && userId != null && node.getType() != null) {
            hasToken = checkOAuthToken(userId, node.getType());
            if (!hasToken) {
                missingFields.add("oauth_token");
            }
        }

        boolean choiceable = node.getOutputDataType() != null
                && !node.getOutputDataType().isBlank();

        boolean executable = configured && (!needsAuth || hasToken);

        return NodeStatusResponse.builder()
                .nodeId(node.getId())
                .configured(configured)
                .saveable(true)
                .choiceable(choiceable)
                .executable(executable)
                .missingFields(missingFields.isEmpty() ? null : missingFields)
                .build();
    }

    private boolean evaluateStartNode(NodeDefinition node, List<String> missingFields) {
        boolean configured = true;

        if (isBlankString(node.getType())) {
            missingFields.add("type");
            configured = false;
        }

        Map<String, Object> config = node.getConfig();

        // source_mode: non-blank 필수
        String sourceMode = config != null ? (String) config.get("source_mode") : null;
        if (isBlankString(sourceMode)) {
            missingFields.add("config.source_mode");
            configured = false;
        }

        // outputDataType: non-blank 필수
        if (isBlankString(node.getOutputDataType())) {
            missingFields.add("outputDataType");
            configured = false;
        }

        // target: source mode의 target_schema가 비어 있지 않은 경우에만 필수
        if (!isBlankString(node.getType()) && !isBlankString(sourceMode)) {
            boolean targetRequired = catalogService.isSourceTargetRequired(node.getType(), sourceMode);
            if (targetRequired) {
                Object target = config != null ? config.get("target") : null;
                if (isMissingValue(target)) {
                    missingFields.add("config.target");
                    configured = false;
                }
            }
        } else if (config == null || !config.containsKey("target")) {
            // type이나 source_mode를 모를 때는 기존처럼 target 키 존재 여부로 판단
            missingFields.add("config.target");
            configured = false;
        }

        return configured;
    }

    private boolean evaluateEndNode(NodeDefinition node, List<String> missingFields) {
        boolean configured = true;

        if (isBlankString(node.getType())) {
            missingFields.add("type");
            configured = false;
        }

        // sink의 필수 config 필드: 값 기반 검증
        if (node.getType() != null && !node.getType().isBlank()) {
            List<String> requiredFields = catalogService.getSinkRequiredFields(node.getType());
            Map<String, Object> config = node.getConfig();
            for (String field : requiredFields) {
                Object value = config != null ? config.get(field) : null;
                if (isMissingValue(value)) {
                    missingFields.add("config." + field);
                    configured = false;
                }
            }
        }

        return configured;
    }

    private boolean evaluateMiddleNode(NodeDefinition node, List<String> missingFields) {
        boolean configured = true;

        if (isBlankString(node.getCategory())) {
            missingFields.add("category");
            configured = false;
        }
        if (isBlankString(node.getType())) {
            missingFields.add("type");
            configured = false;
        }
        if (isBlankString(node.getOutputDataType())) {
            missingFields.add("outputDataType");
            configured = false;
        }

        return configured;
    }

    private boolean checkOAuthToken(String userId, String serviceKey) {
        try {
            oauthTokenService.getDecryptedToken(userId, serviceKey);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 값이 의미 없는지 판단합니다.
     * - null -> missing
     * - 문자열: trim() 후 빈 값이면 missing
     * - Collection: 비어 있으면 missing
     * - Map: 비어 있으면 missing
     */
    private static boolean isMissingValue(Object value) {
        if (value == null) {
            return true;
        }
        if (value instanceof String s) {
            return s.trim().isEmpty();
        }
        if (value instanceof Collection<?> c) {
            return c.isEmpty();
        }
        if (value instanceof Map<?, ?> m) {
            return m.isEmpty();
        }
        // 숫자, boolean 등은 값이 있으면 유효
        return false;
    }

    private static boolean isBlankString(String value) {
        return value == null || value.isBlank();
    }
}
