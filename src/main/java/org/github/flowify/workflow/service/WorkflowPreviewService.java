package org.github.flowify.workflow.service;

import lombok.RequiredArgsConstructor;
import org.github.flowify.catalog.service.CatalogService;
import org.github.flowify.catalog.service.NodeLifecycleService;
import org.github.flowify.common.exception.BusinessException;
import org.github.flowify.common.exception.ErrorCode;
import org.github.flowify.execution.service.FastApiClient;
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
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class WorkflowPreviewService {

    private static final int DEFAULT_LIMIT = 5;
    private static final int MAX_LIMIT = 20;

    private final WorkflowService workflowService;
    private final NodeLifecycleService nodeLifecycleService;
    private final FastApiClient fastApiClient;
    private final WorkflowTranslator workflowTranslator;
    private final OAuthTokenService oauthTokenService;
    private final CatalogService catalogService;

    public NodePreviewResponse previewNode(String userId, String workflowId, String nodeId,
                                           NodePreviewRequest request) {
        Workflow workflow = workflowService.findWorkflowOrThrow(workflowId);
        verifyOwnership(workflow, userId);

        NodeDefinition node = findNodeOrThrow(workflow, nodeId);
        NodeStatusResponse status = nodeLifecycleService.evaluate(node, userId);

        int limit = resolveLimit(request);
        boolean includeContent = request != null && Boolean.TRUE.equals(request.getIncludeContent());
        Map<String, Object> metadata = Map.of(
                "limit", limit,
                "includeContent", includeContent,
                "nodeRole", nullSafe(node.getRole()),
                "nodeType", nullSafe(node.getType())
        );

        if (!status.isConfigured() || !status.isExecutable()) {
            return NodePreviewResponse.builder()
                    .workflowId(workflowId)
                    .nodeId(nodeId)
                    .status("unavailable")
                    .available(false)
                    .reason(resolveUnavailableReason(status.getMissingFields()))
                    .missingFields(status.getMissingFields())
                    .metadata(metadata)
                    .build();
        }

        try {
            Map<String, String> serviceTokens = collectServiceTokens(userId, workflow.getNodes());
            Map<String, Object> runtimeModel = workflowTranslator.toRuntimeModel(workflow);
            return fastApiClient.previewNode(
                    workflowId, userId, nodeId, runtimeModel, serviceTokens, limit, includeContent);
        } catch (BusinessException e) {
            return NodePreviewResponse.builder()
                    .workflowId(workflowId)
                    .nodeId(nodeId)
                    .status(isOAuthError(e.getErrorCode()) ? "unavailable" : "failed")
                    .available(false)
                    .reason(e.getErrorCode().name())
                    .metadata(metadata)
                    .build();
        }
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

    private Map<String, String> collectServiceTokens(String userId, List<NodeDefinition> nodes) {
        Map<String, String> tokens = new HashMap<>();

        nodes.stream()
                .map(NodeDefinition::getType)
                .filter(Objects::nonNull)
                .distinct()
                .filter(catalogService::isAuthRequired)
                .forEach(service ->
                        tokens.put(service, oauthTokenService.getDecryptedToken(userId, service)));

        return tokens;
    }
}
