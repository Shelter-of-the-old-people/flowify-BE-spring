package org.github.flowify.catalog.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.github.flowify.common.exception.BusinessException;
import org.github.flowify.common.exception.ErrorCode;
import org.github.flowify.oauth.service.OAuthTokenService;
import org.github.flowify.workflow.dto.NodeStatusResponse;
import org.github.flowify.workflow.entity.NodeDefinition;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class NodeLifecycleService {

    private static final String GMAIL_READONLY_SCOPE = "https://www.googleapis.com/auth/gmail.readonly";
    private static final String GMAIL_SEND_SCOPE = "https://www.googleapis.com/auth/gmail.send";
    // TODO: Temporary AI generation bridge until the FE Gmail settings panel supports recipient source UX.
    private static final String CURRENT_USER_EMAIL_RECIPIENT_SOURCE = "current_user_email";
    private static final Pattern GITHUB_REPOSITORY_TARGET_PATTERN =
            Pattern.compile("^[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+$");

    private final CatalogService catalogService;
    private final OAuthTokenService oauthTokenService;

    private enum TokenCheckMode {
        ACTIVE_TOKEN,
        STATUS_ONLY
    }

    public List<NodeStatusResponse> evaluateAll(List<NodeDefinition> nodes, String userId) {
        if (nodes == null) {
            return List.of();
        }
        return nodes.stream()
                .map(node -> evaluate(node, userId))
                .toList();
    }

    public List<NodeStatusResponse> evaluateAllForStatusCheck(List<NodeDefinition> nodes, String userId) {
        if (nodes == null) {
            return List.of();
        }
        return nodes.stream()
                .map(node -> evaluate(node, userId, TokenCheckMode.STATUS_ONLY))
                .toList();
    }

    public NodeStatusResponse evaluate(NodeDefinition node, String userId) {
        return evaluate(node, userId, TokenCheckMode.ACTIVE_TOKEN);
    }

    private NodeStatusResponse evaluate(NodeDefinition node, String userId, TokenCheckMode tokenCheckMode) {
        List<String> missingFields = new ArrayList<>();
        boolean configured;
        String serviceKey = resolveServiceKey(node);

        String role = node.getRole();
        boolean needsAuth;
        if ("start".equals(role)) {
            configured = evaluateStartNode(node, missingFields);
            needsAuth = catalogService.isAuthRequired(serviceKey);
        } else if ("end".equals(role)) {
            configured = evaluateEndNode(node, missingFields);
            needsAuth = catalogService.isAuthRequired(serviceKey);
        } else {
            configured = evaluateMiddleNode(node, missingFields);
            needsAuth = catalogService.isAuthRequired(serviceKey);
        }

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
        if (needsAuth && userId != null && serviceKey != null) {
            hasToken = checkOAuthToken(userId, node, serviceKey, missingFields, tokenCheckMode);
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
        String sourceMode = config != null ? asString(config.get("source_mode")) : null;
        if (isBlankString(sourceMode)) {
            missingFields.add("config.source_mode");
            configured = false;
        }

        if (isBlankString(node.getOutputDataType())) {
            missingFields.add("outputDataType");
            configured = false;
        }

        if (!isBlankString(node.getType()) && !isBlankString(sourceMode)) {
            boolean targetRequired = catalogService.isSourceTargetRequired(node.getType(), sourceMode);
            if (targetRequired) {
                Object target = resolveSourceTargetValue(node.getType(), config);
                if (isMissingValue(target)) {
                    missingFields.add("config.target");
                    configured = false;
                }
            }
        } else if (config == null || !config.containsKey("target")) {
            missingFields.add("config.target");
            configured = false;
        }

        if ("google_sheets".equals(node.getType())) {
            configured = evaluateGoogleSheetsStartNode(config, sourceMode, missingFields, configured);
        }
        if ("github".equals(node.getType())) {
            configured = evaluateGithubStartNode(config, sourceMode, missingFields, configured);
        }

        return configured;
    }

    private boolean evaluateEndNode(NodeDefinition node, List<String> missingFields) {
        boolean configured = true;

        if (isBlankString(node.getType())) {
            missingFields.add("type");
            configured = false;
        }

        if (node.getType() != null && !node.getType().isBlank()) {
            List<String> requiredFields = catalogService.getSinkRequiredFields(node.getType());
            Map<String, Object> config = node.getConfig();
            for (String field : requiredFields) {
                Object value = config != null ? config.get(field) : null;
                if (isMissingValue(value) && !isSatisfiedByRuntimeRecipient(node.getType(), field, config)) {
                    missingFields.add("config." + field);
                    configured = false;
                }
            }
        }

        if ("google_sheets".equals(node.getType())) {
            configured = evaluateGoogleSheetsEndNode(node.getConfig(), missingFields, configured);
        }

        return configured;
    }

    private boolean isSatisfiedByRuntimeRecipient(
            String serviceKey,
            String field,
            Map<String, Object> config
    ) {
        if (!"gmail".equals(serviceKey) || !"to".equals(field) || config == null) {
            return false;
        }
        return CURRENT_USER_EMAIL_RECIPIENT_SOURCE.equals(asText(config.get("to_source")));
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

        if ("google_sheets".equals(resolveServiceKey(node))) {
            configured = evaluateGoogleSheetsMiddleNode(node.getConfig(), missingFields, configured);
        }

        return configured;
    }

    private boolean checkOAuthToken(
            String userId,
            NodeDefinition node,
            String serviceKey,
            List<String> missingFields,
            TokenCheckMode tokenCheckMode
    ) {
        try {
            List<String> scopes = requiredScopes(node, serviceKey);
            if (tokenCheckMode == TokenCheckMode.STATUS_ONLY) {
                oauthTokenService.validateTokenForStatusCheck(userId, serviceKey, scopes);
            } else {
                oauthTokenService.getDecryptedToken(userId, serviceKey, scopes);
            }
            return true;
        } catch (BusinessException e) {
            if (e.getErrorCode() == ErrorCode.OAUTH_SCOPE_INSUFFICIENT) {
                missingFields.add("oauth_scope_insufficient");
            } else {
                missingFields.add("oauth_token");
            }
            return false;
        } catch (Exception e) {
            missingFields.add("oauth_token");
            return false;
        }
    }

    private List<String> requiredScopes(NodeDefinition node, String serviceKey) {
        if (!"gmail".equals(serviceKey)) {
            return List.of();
        }
        if ("start".equals(node.getRole())) {
            return List.of(GMAIL_READONLY_SCOPE);
        }
        if ("end".equals(node.getRole())) {
            return List.of(GMAIL_SEND_SCOPE);
        }
        return List.of();
    }

    private boolean evaluateGoogleSheetsStartNode(
            Map<String, Object> config,
            String sourceMode,
            List<String> missingFields,
            boolean configured
    ) {
        if (config == null) {
            missingFields.add("config.sheet_name");
            return false;
        }

        if (isMissingValue(config.get("sheet_name"))) {
            missingFields.add("config.sheet_name");
            configured = false;
        }

        if ("row_updated".equals(sourceMode) && isMissingValue(config.get("key_column"))) {
            missingFields.add("config.key_column");
            configured = false;
        }

        return configured;
    }

    private boolean evaluateGoogleSheetsEndNode(
            Map<String, Object> config,
            List<String> missingFields,
            boolean configured
    ) {
        if (config == null) {
            missingFields.add("config.sheet_name");
            return false;
        }

        if (isMissingValue(config.get("sheet_name"))) {
            missingFields.add("config.sheet_name");
            configured = false;
        }

        String writeMode = asText(config.get("write_mode"));
        if (("update_row_by_key".equals(writeMode) || "upsert_row_by_key".equals(writeMode))
                && isMissingValue(config.get("key_column"))) {
            missingFields.add("config.key_column");
            configured = false;
        }

        return configured;
    }

    private boolean evaluateGithubStartNode(
            Map<String, Object> config,
            String sourceMode,
            List<String> missingFields,
            boolean configured
    ) {
        if (!"new_pr".equals(sourceMode)) {
            return configured;
        }

        Object target = resolveSourceTargetValue("github", config);
        if (!isValidGitHubRepoTarget(target)) {
            missingFields.add("config.target");
            configured = false;
        }

        return configured;
    }

    private boolean evaluateGoogleSheetsMiddleNode(
            Map<String, Object> config,
            List<String> missingFields,
            boolean configured
    ) {
        if (config == null) {
            missingFields.add("config.action");
            return false;
        }

        if (isMissingValue(config.get("action"))) {
            missingFields.add("config.action");
            configured = false;
        }
        if (isMissingValue(config.get("spreadsheet_id"))) {
            missingFields.add("config.spreadsheet_id");
            configured = false;
        }
        if (isMissingValue(config.get("sheet_name"))) {
            missingFields.add("config.sheet_name");
            configured = false;
        }

        String action = asText(config.get("action"));
        if ("lookup_row_by_key".equals(action) && isMissingValue(config.get("key_column"))) {
            missingFields.add("config.key_column");
            configured = false;
        }
        if ("search_text".equals(action) && isMissingValue(config.get("search_value"))) {
            boolean usesBoundInput = "input_field".equals(asText(config.get("search_source")))
                    && !isMissingValue(config.get("search_field"));
            if (!usesBoundInput) {
                missingFields.add("config.search_value");
                configured = false;
            }
        }
        if ("lookup_row_by_key".equals(action) && isMissingValue(config.get("lookup_value"))) {
            boolean usesBoundInput = "input_field".equals(asText(config.get("lookup_source")))
                    && !isMissingValue(config.get("lookup_field"));
            if (!usesBoundInput) {
                missingFields.add("config.lookup_value");
                configured = false;
            }
        }

        return configured;
    }

    private Object resolveSourceTargetValue(String serviceKey, Map<String, Object> config) {
        if (config == null) {
            return null;
        }

        if ("google_sheets".equals(serviceKey) && !isMissingValue(config.get("spreadsheet_id"))) {
            return config.get("spreadsheet_id");
        }

        return config.get("target");
    }

    private String resolveServiceKey(NodeDefinition node) {
        if (node == null) {
            return null;
        }

        if (node.getType() != null && !node.getType().isBlank() && !"spreadsheet".equals(node.getType())) {
            return node.getType();
        }

        Map<String, Object> config = node.getConfig();
        if (config == null) {
            return node.getType();
        }

        Object service = config.get("service");
        if (service instanceof String stringValue && !stringValue.isBlank()) {
            return stringValue;
        }
        return node.getType();
    }

    public static boolean isValidGitHubRepoTarget(Object target) {
        if (!(target instanceof String text)) {
            return false;
        }

        String normalized = text.trim();
        if (normalized.isBlank()) {
            return false;
        }

        return GITHUB_REPOSITORY_TARGET_PATTERN.matcher(normalized).matches();
    }

    private String asText(Object value) {
        return value != null ? String.valueOf(value).trim() : "";
    }

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
        return false;
    }

    private static boolean isBlankString(String value) {
        return value == null || value.isBlank();
    }

    private static String asString(Object value) {
        return value instanceof String text ? text : null;
    }
}
