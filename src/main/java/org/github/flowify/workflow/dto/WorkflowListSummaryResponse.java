package org.github.flowify.workflow.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import org.github.flowify.workflow.entity.NodeDefinition;
import org.github.flowify.workflow.entity.Workflow;

import java.util.List;
import java.util.Map;

@Getter
@Builder
@AllArgsConstructor
public class WorkflowListSummaryResponse {

    private final int totalNodeCount;
    private final int configuredNodeCount;
    private final WorkflowListNodeSummaryResponse startNode;
    private final List<WorkflowListNodeSummaryResponse> endNodes;

    public static WorkflowListSummaryResponse from(Workflow workflow) {
        List<NodeDefinition> nodes = workflow.getNodes() != null ? workflow.getNodes() : List.of();
        return fromNodes(nodes);
    }

    public static WorkflowListSummaryResponse fromNodes(List<NodeDefinition> nodes) {
        nodes = nodes != null ? nodes : List.of();
        NodeDefinition startNode = resolveStartNode(nodes);
        List<NodeDefinition> endNodes = resolveEndNodes(nodes);

        return WorkflowListSummaryResponse.builder()
                .totalNodeCount(nodes.size())
                .configuredNodeCount((int) nodes.stream().filter(WorkflowListSummaryResponse::isConfigured).count())
                .startNode(WorkflowListNodeSummaryResponse.from(startNode))
                .endNodes(endNodes.stream()
                        .map(WorkflowListNodeSummaryResponse::from)
                        .toList())
                .build();
    }

    private static NodeDefinition resolveStartNode(List<NodeDefinition> nodes) {
        if (nodes.isEmpty()) {
            return null;
        }

        return nodes.stream()
                .filter(node -> "start".equals(node.getRole()))
                .findFirst()
                .orElse(nodes.get(0));
    }

    private static List<NodeDefinition> resolveEndNodes(List<NodeDefinition> nodes) {
        if (nodes.size() <= 1) {
            return List.of();
        }

        List<NodeDefinition> roleEndNodes = nodes.stream()
                .filter(node -> "end".equals(node.getRole()))
                .toList();
        if (!roleEndNodes.isEmpty()) {
            return roleEndNodes;
        }

        return List.of(nodes.get(nodes.size() - 1));
    }

    private static boolean isConfigured(NodeDefinition node) {
        Map<String, Object> config = node.getConfig();
        return config != null && Boolean.TRUE.equals(config.get("isConfigured"));
    }
}
