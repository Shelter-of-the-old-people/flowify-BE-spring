package org.github.flowify.workflow.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import org.github.flowify.workflow.entity.NodeDefinition;

import java.util.Map;

@Getter
@Builder
@AllArgsConstructor
public class WorkflowListNodeSummaryResponse {

    private final String id;
    private final String category;
    private final String type;
    private final String label;
    private final String role;
    private final String service;
    private final String sourceMode;

    public static WorkflowListNodeSummaryResponse from(NodeDefinition node) {
        if (node == null) {
            return null;
        }

        Map<String, Object> config = node.getConfig();
        return WorkflowListNodeSummaryResponse.builder()
                .id(node.getId())
                .category(node.getCategory())
                .type(node.getType())
                .label(node.getLabel())
                .role(node.getRole())
                .service(textValue(config, "service"))
                .sourceMode(textValue(config, "source_mode"))
                .build();
    }

    private static String textValue(Map<String, Object> config, String key) {
        if (config == null) {
            return null;
        }

        Object value = config.get(key);
        return value != null ? String.valueOf(value) : null;
    }
}
