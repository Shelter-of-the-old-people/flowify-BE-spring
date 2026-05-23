package org.github.flowify.workflow.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import org.github.flowify.workflow.entity.NodeDefinition;
import org.github.flowify.workflow.entity.Workflow;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Getter
@Builder
@AllArgsConstructor
public class WorkflowListReadinessResponse {

    private final boolean executable;
    private final int blockerCount;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private final WorkflowListNodeSummaryResponse firstBlockerNode;
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    private final List<String> firstMissingFields;

    public static WorkflowListReadinessResponse from(Workflow workflow) {
        List<NodeDefinition> nodes = workflow != null && workflow.getNodes() != null
                ? workflow.getNodes()
                : List.of();
        return fromNodes(nodes);
    }

    public static WorkflowListReadinessResponse fromNodes(List<NodeDefinition> nodes) {
        nodes = nodes != null ? nodes : List.of();
        if (nodes.isEmpty()) {
            return WorkflowListReadinessResponse.builder()
                    .executable(false)
                    .blockerCount(1)
                    .firstMissingFields(List.of("nodes"))
                    .build();
        }

        List<NodeReadinessBlocker> blockers = nodes.stream()
                .map(WorkflowListReadinessResponse::toBlocker)
                .filter(NodeReadinessBlocker::blocked)
                .toList();
        NodeReadinessBlocker firstBlocker = blockers.isEmpty() ? null : blockers.get(0);

        return WorkflowListReadinessResponse.builder()
                .executable(blockers.isEmpty())
                .blockerCount(blockers.size())
                .firstBlockerNode(firstBlocker != null
                        ? WorkflowListNodeSummaryResponse.from(firstBlocker.node())
                        : null)
                .firstMissingFields(firstBlocker != null ? firstBlocker.missingFields() : List.of())
                .build();
    }

    private static NodeReadinessBlocker toBlocker(NodeDefinition node) {
        List<String> missingFields = new ArrayList<>();

        if (isBlank(node.getType())) {
            missingFields.add("type");
        }

        String role = node.getRole();
        if ("start".equals(role)) {
            addStartNodeMissingFields(node, missingFields);
        } else if ("middle".equals(role)) {
            addMiddleNodeMissingFields(node, missingFields);
        }

        Map<String, Object> config = node.getConfig();
        if (config != null && Boolean.FALSE.equals(config.get("isConfigured"))) {
            missingFields.add("config");
        }

        return new NodeReadinessBlocker(node, missingFields);
    }

    private static void addStartNodeMissingFields(NodeDefinition node, List<String> missingFields) {
        Map<String, Object> config = node.getConfig();
        Object sourceMode = config != null ? config.get("source_mode") : null;
        if (isBlank(sourceMode)) {
            missingFields.add("config.source_mode");
        }
        if (isBlank(node.getOutputDataType())) {
            missingFields.add("outputDataType");
        }
    }

    private static void addMiddleNodeMissingFields(NodeDefinition node, List<String> missingFields) {
        if (isBlank(node.getCategory())) {
            missingFields.add("category");
        }
        if (isBlank(node.getOutputDataType())) {
            missingFields.add("outputDataType");
        }
    }

    private static boolean isBlank(Object value) {
        return value == null || String.valueOf(value).isBlank();
    }

    private record NodeReadinessBlocker(NodeDefinition node, List<String> missingFields) {

        private boolean blocked() {
            return !missingFields.isEmpty();
        }
    }
}
