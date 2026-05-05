package org.github.flowify.workflow.service;

import lombok.RequiredArgsConstructor;
import org.github.flowify.catalog.service.NodeLifecycleService;
import org.github.flowify.common.exception.BusinessException;
import org.github.flowify.common.exception.ErrorCode;
import org.github.flowify.workflow.dto.NodePreviewRequest;
import org.github.flowify.workflow.dto.NodePreviewResponse;
import org.github.flowify.workflow.dto.NodeStatusResponse;
import org.github.flowify.workflow.entity.NodeDefinition;
import org.github.flowify.workflow.entity.Workflow;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class WorkflowPreviewService {

    private static final int DEFAULT_LIMIT = 5;
    private static final int MAX_LIMIT = 20;

    private final WorkflowService workflowService;
    private final NodeLifecycleService nodeLifecycleService;

    public NodePreviewResponse previewNode(String userId, String workflowId, String nodeId,
                                           NodePreviewRequest request) {
        Workflow workflow = workflowService.findWorkflowOrThrow(workflowId);
        verifyOwnership(workflow, userId);

        NodeDefinition node = findNodeOrThrow(workflow, nodeId);
        NodeStatusResponse status = nodeLifecycleService.evaluate(node, userId);

        Map<String, Object> metadata = Map.of(
                "limit", resolveLimit(request),
                "includeContent", request != null && Boolean.TRUE.equals(request.getIncludeContent()),
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

        return NodePreviewResponse.builder()
                .workflowId(workflowId)
                .nodeId(nodeId)
                .status("unavailable")
                .available(false)
                .reason("PREVIEW_NOT_IMPLEMENTED")
                .metadata(metadata)
                .build();
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
}
