package org.github.flowify.workflow.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import org.github.flowify.workflow.entity.EdgeDefinition;
import org.github.flowify.workflow.entity.NodeDefinition;
import org.github.flowify.workflow.entity.TriggerConfig;
import org.github.flowify.workflow.entity.Workflow;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;

@Getter
@Builder
@AllArgsConstructor
public class WorkflowResponse {

    private final String id;
    private final String name;
    private final String description;
    private final String userId;
    private final List<String> sharedWith;
    @com.fasterxml.jackson.annotation.JsonProperty("isTemplate")
    private final boolean isTemplate;
    private final String templateId;
    private final List<NodeDefinition> nodes;
    private final List<EdgeDefinition> edges;
    private final TriggerConfig trigger;
    private final boolean isActive;
    private final Instant createdAt;
    private final Instant updatedAt;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private final List<ValidationWarning> warnings;

    public static WorkflowResponse from(Workflow workflow) {
        return WorkflowResponse.builder()
                .id(workflow.getId())
                .name(workflow.getName())
                .description(workflow.getDescription())
                .userId(workflow.getUserId())
                .sharedWith(workflow.getSharedWith())
                .isTemplate(workflow.isTemplate())
                .templateId(workflow.getTemplateId())
                .nodes(workflow.getNodes())
                .edges(workflow.getEdges())
                .trigger(workflow.getTrigger())
                .isActive(workflow.isActive())
                .createdAt(workflow.getCreatedAt())
                .updatedAt(workflow.getUpdatedAt())
                .build();
    }

    public static WorkflowResponse from(Workflow workflow, List<ValidationWarning> warnings) {
        return WorkflowResponse.builder()
                .id(workflow.getId())
                .name(workflow.getName())
                .description(workflow.getDescription())
                .userId(workflow.getUserId())
                .sharedWith(workflow.getSharedWith())
                .isTemplate(workflow.isTemplate())
                .templateId(workflow.getTemplateId())
                .nodes(workflow.getNodes())
                .edges(workflow.getEdges())
                .trigger(workflow.getTrigger())
                .isActive(workflow.isActive())
                .createdAt(workflow.getCreatedAt())
                .updatedAt(workflow.getUpdatedAt())
                .warnings(warnings.isEmpty() ? null : warnings)
                .build();
    }
}
