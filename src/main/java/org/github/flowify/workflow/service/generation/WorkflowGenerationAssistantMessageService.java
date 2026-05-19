package org.github.flowify.workflow.service.generation;

import org.github.flowify.workflow.dto.NodeStatusResponse;
import org.github.flowify.workflow.dto.WorkflowGenerationNextAction;
import org.github.flowify.workflow.dto.WorkflowGenerationResultResponse;
import org.github.flowify.workflow.dto.WorkflowGenerationStatus;
import org.github.flowify.workflow.dto.WorkflowResponse;
import org.github.flowify.workflow.entity.NodeDefinition;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class WorkflowGenerationAssistantMessageService {

    private static final int MAX_DISPLAY_NODE_COUNT = 3;

    public WorkflowGenerationResultResponse buildResult(WorkflowResponse workflow) {
        boolean needsConfiguration = hasConfigurationIssue(workflow);
        List<String> nodeNames = findConfigurationIssueNodeNames(workflow);

        return WorkflowGenerationResultResponse.builder()
                .workflow(workflow)
                .assistantMessage(buildAssistantMessage(needsConfiguration, nodeNames))
                .status(needsConfiguration
                        ? WorkflowGenerationStatus.NEEDS_CONFIGURATION
                        : WorkflowGenerationStatus.GENERATED)
                .requiresUserAction(needsConfiguration)
                .nextActions(resolveNextActions(needsConfiguration))
                .build();
    }

    private boolean hasConfigurationIssue(WorkflowResponse workflow) {
        if (workflow == null || workflow.getNodeStatuses() == null) {
            return false;
        }

        return workflow.getNodeStatuses().stream()
                .anyMatch(status -> status != null && !status.isConfigured());
    }

    private List<String> findConfigurationIssueNodeNames(WorkflowResponse workflow) {
        if (workflow == null || workflow.getNodeStatuses() == null || workflow.getNodes() == null) {
            return List.of();
        }

        Map<String, NodeDefinition> nodesById = new LinkedHashMap<>();
        for (NodeDefinition node : workflow.getNodes()) {
            if (node != null && hasText(node.getId())) {
                nodesById.put(node.getId(), node);
            }
        }

        Set<String> names = new LinkedHashSet<>();
        for (NodeStatusResponse status : workflow.getNodeStatuses()) {
            if (status == null || status.isConfigured()) {
                continue;
            }

            NodeDefinition node = nodesById.get(status.getNodeId());
            String displayName = displayName(node);
            if (hasText(displayName)) {
                names.add(displayName);
            }
        }

        return new ArrayList<>(names);
    }

    private String buildAssistantMessage(boolean needsConfiguration, List<String> nodeNames) {
        if (!needsConfiguration) {
            return "워크플로우 초안을 만들었어요. 화면에서 흐름을 검토한 뒤 실행할 수 있습니다.";
        }

        if (nodeNames == null || nodeNames.isEmpty()) {
            return "워크플로우 초안을 만들었어요. 설정이 필요한 노드를 확인한 뒤 실행할 수 있습니다.";
        }

        String target = formatNodeNames(nodeNames);
        return "워크플로우 초안을 만들었어요. " + target + " 설정을 확인한 뒤 실행할 수 있습니다.";
    }

    private List<WorkflowGenerationNextAction> resolveNextActions(boolean needsConfiguration) {
        if (needsConfiguration) {
            return List.of(
                    WorkflowGenerationNextAction.REVIEW_WORKFLOW,
                    WorkflowGenerationNextAction.CONFIGURE_NODES
            );
        }

        return List.of(WorkflowGenerationNextAction.REVIEW_WORKFLOW);
    }

    private String formatNodeNames(List<String> nodeNames) {
        int displayCount = Math.min(nodeNames.size(), MAX_DISPLAY_NODE_COUNT);
        String joinedNames = String.join(", ", nodeNames.subList(0, displayCount));
        int remainingCount = nodeNames.size() - displayCount;

        if (remainingCount <= 0) {
            return joinedNames;
        }

        return joinedNames + " 외 " + remainingCount + "개";
    }

    private String displayName(NodeDefinition node) {
        if (node == null) {
            return null;
        }

        if (hasText(node.getLabel())) {
            return node.getLabel().trim();
        }

        if (hasText(node.getType())) {
            return node.getType().trim();
        }

        return null;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
