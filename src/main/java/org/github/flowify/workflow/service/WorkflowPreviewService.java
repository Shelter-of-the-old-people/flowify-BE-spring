package org.github.flowify.workflow.service;

import lombok.RequiredArgsConstructor;
import org.github.flowify.catalog.service.CatalogService;
import org.github.flowify.catalog.service.NodeLifecycleService;
import org.github.flowify.common.exception.BusinessException;
import org.github.flowify.common.exception.ErrorCode;
import org.github.flowify.execution.service.FastApiClient;
import org.github.flowify.execution.service.RuntimeContextService;
import org.github.flowify.execution.service.WorkflowTranslator;
import org.github.flowify.oauth.service.OAuthTokenService;
import org.github.flowify.workflow.dto.NodePreviewRequest;
import org.github.flowify.workflow.dto.NodePreviewResponse;
import org.github.flowify.workflow.dto.NodeStatusResponse;
import org.github.flowify.workflow.entity.NodeDefinition;
import org.github.flowify.workflow.entity.Workflow;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class WorkflowPreviewService {

    private static final int DEFAULT_LIMIT = 5;
    private static final int MAX_LIMIT = 20;
    private static final String GMAIL_READONLY_SCOPE = "https://www.googleapis.com/auth/gmail.readonly";

    private final WorkflowService workflowService;
    private final NodeLifecycleService nodeLifecycleService;
    private final FastApiClient fastApiClient;
    private final WorkflowTranslator workflowTranslator;
    private final OAuthTokenService oauthTokenService;
    private final CatalogService catalogService;
    private final RuntimeContextService runtimeContextService;

    public NodePreviewResponse previewNode(String userId, String workflowId, String nodeId,
                                            NodePreviewRequest request) {
        Workflow workflow = workflowService.findWorkflowOrThrow(workflowId);
        verifyOwnership(workflow, userId);

        NodeDefinition node = findNodeOrThrow(workflow, nodeId);
        int limit = resolveLimit(request);
        boolean includeContent = request != null && Boolean.TRUE.equals(request.getIncludeContent());
        Map<String, Object> unavailableMetadata = createPreviewMetadata(
                node, limit, includeContent, "metadata_only", false);

        if (!isSourcePreviewSupported(node)) {
            return NodePreviewResponse.builder()
                    .workflowId(workflowId)
                    .nodeId(nodeId)
                    .status("unavailable")
                    .available(false)
                    .reason("PREVIEW_NOT_IMPLEMENTED")
                    .metadata(unavailableMetadata)
                    .build();
        }

        NodeStatusResponse status = nodeLifecycleService.evaluate(node, userId);

        if (!status.isConfigured() || !status.isExecutable()) {
            return NodePreviewResponse.builder()
                    .workflowId(workflowId)
                    .nodeId(nodeId)
                    .status("unavailable")
                    .available(false)
                    .reason(resolveUnavailableReason(status.getMissingFields()))
                    .missingFields(status.getMissingFields())
                    .metadata(unavailableMetadata)
                    .build();
        }

        try {
            Map<String, String> serviceTokens = collectPreviewServiceTokens(userId, node);
            Map<String, Object> runtimeModel = workflowTranslator.toRuntimeModel(workflow);
            NodePreviewResponse response = fastApiClient.previewNode(
                    workflowId,
                    userId,
                    nodeId,
                    runtimeModel,
                    serviceTokens,
                    limit,
                    includeContent,
                    runtimeContextFor(userId)
            );
            return enrichPreviewMetadata(response, node, limit, includeContent);
        } catch (BusinessException e) {
            return NodePreviewResponse.builder()
                    .workflowId(workflowId)
                    .nodeId(nodeId)
                    .status(isOAuthError(e.getErrorCode()) ? "unavailable" : "failed")
                    .available(false)
                    .reason(e.getErrorCode().name())
                    .metadata(unavailableMetadata)
                    .build();
        }
    }

    private NodePreviewResponse enrichPreviewMetadata(NodePreviewResponse response, NodeDefinition node,
                                                      int limit, boolean includeContent) {
        boolean contentIncluded = includeContent && hasIncludedContent(response);
        boolean contentStatusPresent = hasContentStatus(response);
        String contentPolicy = resolveContentPolicy(includeContent, contentIncluded, contentStatusPresent);
        Map<String, Object> metadata = createPreviewMetadata(
                node, limit, includeContent, contentPolicy, contentIncluded);
        if (response.getMetadata() != null) {
            response.getMetadata().forEach((key, value) -> {
                if (value != null) {
                    metadata.put(key, value);
                }
            });
        }

        return NodePreviewResponse.builder()
                .workflowId(response.getWorkflowId())
                .nodeId(response.getNodeId())
                .status(response.getStatus())
                .available(response.isAvailable())
                .reason(response.getReason())
                .inputData(response.getInputData())
                .outputData(response.getOutputData())
                .previewData(response.getPreviewData())
                .missingFields(response.getMissingFields())
                .metadata(metadata)
                .build();
    }

    private Map<String, Object> createPreviewMetadata(NodeDefinition node, int limit,
                                                      boolean includeContent,
                                                      String contentPolicy,
                                                      boolean contentIncluded) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("limit", limit);
        metadata.put("includeContent", includeContent);
        metadata.put("previewScope", "source_metadata");
        metadata.put("contentPolicy", contentPolicy);
        metadata.put("contentIncluded", contentIncluded);
        metadata.put("contentStatusScope", "none");
        metadata.put("contentRequired", false);
        metadata.put("contentRequiredReason", null);
        metadata.put("nodeRole", nullSafe(node.getRole()));
        metadata.put("nodeType", nullSafe(node.getType()));
        return metadata;
    }

    private String resolveContentPolicy(boolean includeContent, boolean contentIncluded, boolean contentStatusPresent) {
        if (contentIncluded) {
            return "content_included";
        }
        if (includeContent && contentStatusPresent) {
            return "content_status_only";
        }
        return "metadata_only";
    }

    private boolean hasIncludedContent(NodePreviewResponse response) {
        return hasIncludedContent(response.getPreviewData())
                || hasIncludedContent(response.getOutputData())
                || hasIncludedContent(response.getInputData());
    }

    private boolean hasIncludedContent(Object value) {
        if (value instanceof Map<?, ?> map) {
            if ("available".equals(firstString(map, "content_status", "contentStatus"))) {
                return true;
            }
            if (hasText(firstString(map, "content", "extracted_text", "extractedText"))) {
                return true;
            }
            return map.values().stream().anyMatch(this::hasIncludedContent);
        }
        if (value instanceof Iterable<?> values) {
            for (Object item : values) {
                if (hasIncludedContent(item)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean hasContentStatus(NodePreviewResponse response) {
        return hasContentStatus(response.getPreviewData())
                || hasContentStatus(response.getOutputData())
                || hasContentStatus(response.getInputData());
    }

    private boolean hasContentStatus(Object value) {
        if (value instanceof Map<?, ?> map) {
            if (firstString(map, "content_status", "contentStatus") != null) {
                return true;
            }
            return map.values().stream().anyMatch(this::hasContentStatus);
        }
        if (value instanceof Iterable<?> values) {
            for (Object item : values) {
                if (hasContentStatus(item)) {
                    return true;
                }
            }
        }
        return false;
    }

    private String firstString(Map<?, ?> map, String... keys) {
        for (String key : keys) {
            Object value = map.get(key);
            if (value instanceof String text) {
                return text;
            }
        }
        return null;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private NodeDefinition findNodeOrThrow(Workflow workflow, String nodeId) {
        List<NodeDefinition> nodes = workflow.getNodes();
        if (nodes == null) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST,
                    "Node '" + nodeId + "' was not found.");
        }

        return nodes.stream()
                .filter(node -> nodeId.equals(node.getId()))
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_REQUEST,
                        "Node '" + nodeId + "' was not found."));
    }

    private void verifyOwnership(Workflow workflow, String userId) {
        if (!workflow.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.WORKFLOW_ACCESS_DENIED);
        }
    }

    private int resolveLimit(NodePreviewRequest request) {
        if (request == null || request.getLimit() == null) {
            return DEFAULT_LIMIT;
        }

        return Math.min(Math.max(request.getLimit(), 1), MAX_LIMIT);
    }

    private String resolveUnavailableReason(List<String> missingFields) {
        if (missingFields == null || missingFields.isEmpty()) {
            return "NODE_NOT_READY";
        }
        if (missingFields.contains("oauth_scope_insufficient")) {
            return "OAUTH_SCOPE_INSUFFICIENT";
        }
        if (missingFields.contains("oauth_token")) {
            return "OAUTH_NOT_CONNECTED";
        }
        return "NODE_NOT_CONFIGURED";
    }

    private String nullSafe(String value) {
        return value != null ? value : "";
    }

    private boolean isOAuthError(ErrorCode errorCode) {
        return errorCode == ErrorCode.OAUTH_NOT_CONNECTED
                || errorCode == ErrorCode.OAUTH_TOKEN_EXPIRED
                || errorCode == ErrorCode.OAUTH_SCOPE_INSUFFICIENT;
    }

    private Map<String, Object> runtimeContextFor(String userId) {
        Map<String, Object> runtimeContext = runtimeContextService.buildForUser(userId);
        return runtimeContext != null ? runtimeContext : Map.of();
    }

    private boolean isSourcePreviewSupported(NodeDefinition node) {
        return "start".equals(node.getRole());
    }

    private Map<String, String> collectPreviewServiceTokens(String userId, NodeDefinition node) {
        Map<String, String> tokens = new HashMap<>();
        String service = node.getType();

        if (service != null && catalogService.isAuthRequired(service)) {
            tokens.put(service, oauthTokenService.getDecryptedToken(
                    userId, service, requiredScopes(node)));
        }

        return tokens;
    }

    private List<String> requiredScopes(NodeDefinition node) {
        if ("gmail".equals(node.getType()) && "start".equals(node.getRole())) {
            return List.of(GMAIL_READONLY_SCOPE);
        }
        return List.of();
    }
}
