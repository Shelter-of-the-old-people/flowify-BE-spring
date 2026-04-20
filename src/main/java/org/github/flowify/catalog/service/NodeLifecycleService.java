package org.github.flowify.catalog.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.github.flowify.oauth.service.OAuthTokenService;
import org.github.flowify.workflow.dto.NodeStatusResponse;
import org.github.flowify.workflow.entity.NodeDefinition;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
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

        if (node.getType() == null || node.getType().isBlank()) {
            missingFields.add("type");
            configured = false;
        }

        Map<String, Object> config = node.getConfig();
        if (config == null || !config.containsKey("source_mode")) {
            missingFields.add("config.source_mode");
            configured = false;
        }
        if (config == null || !config.containsKey("target")) {
            missingFields.add("config.target");
            configured = false;
        }

        if (node.getOutputDataType() == null || node.getOutputDataType().isBlank()) {
            missingFields.add("outputDataType");
            configured = false;
        }

        return configured;
    }

    private boolean evaluateEndNode(NodeDefinition node, List<String> missingFields) {
        boolean configured = true;

        if (node.getType() == null || node.getType().isBlank()) {
            missingFields.add("type");
            configured = false;
        }

        // sink의 필수 config 필드 검사
        if (node.getType() != null) {
            List<String> requiredFields = catalogService.getSinkRequiredFields(node.getType());
            Map<String, Object> config = node.getConfig();
            for (String field : requiredFields) {
                if (config == null || !config.containsKey(field)) {
                    missingFields.add("config." + field);
                    configured = false;
                }
            }
        }

        return configured;
    }

    private boolean evaluateMiddleNode(NodeDefinition node, List<String> missingFields) {
        boolean configured = true;

        if (node.getCategory() == null || node.getCategory().isBlank()) {
            missingFields.add("category");
            configured = false;
        }
        if (node.getType() == null || node.getType().isBlank()) {
            missingFields.add("type");
            configured = false;
        }
        if (node.getOutputDataType() == null || node.getOutputDataType().isBlank()) {
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
}
