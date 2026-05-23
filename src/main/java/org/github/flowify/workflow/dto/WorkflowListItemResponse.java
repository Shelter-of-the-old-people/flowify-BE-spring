package org.github.flowify.workflow.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import org.github.flowify.execution.dto.ExecutionSummaryResponse;
import org.github.flowify.workflow.entity.TriggerConfig;
import org.github.flowify.workflow.entity.Workflow;
import org.github.flowify.workflow.service.WorkflowTriggerSupport;

import java.time.Instant;
import java.util.List;

@Getter
@Builder
@AllArgsConstructor
public class WorkflowListItemResponse {

    private final String id;
    private final String name;
    private final String description;
    private final String userId;
    private final List<String> sharedWith;
    @JsonProperty("isTemplate")
    private final boolean template;
    private final String templateId;
    private final boolean active;
    private final TriggerConfig trigger;
    private final Instant createdAt;
    private final Instant updatedAt;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private final ExecutionSummaryResponse latestExecution;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private final String listStatus;
    private final WorkflowListSummaryResponse summary;
    private final WorkflowListReadinessResponse readiness;
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    private final List<ValidationWarning> warnings;

    public static WorkflowListItemResponse from(
            Workflow workflow,
            ExecutionSummaryResponse latestExecution,
            String listStatus,
            List<ValidationWarning> warnings
    ) {
        TriggerConfig normalizedTrigger = WorkflowTriggerSupport.normalizeTrigger(workflow.getTrigger());
        return WorkflowListItemResponse.builder()
                .id(workflow.getId())
                .name(workflow.getName())
                .description(workflow.getDescription())
                .userId(workflow.getUserId())
                .sharedWith(workflow.getSharedWith())
                .template(workflow.isTemplate())
                .templateId(workflow.getTemplateId())
                .active(WorkflowTriggerSupport.normalizeActive(normalizedTrigger, workflow.isActive()))
                .trigger(normalizedTrigger)
                .createdAt(workflow.getCreatedAt())
                .updatedAt(workflow.getUpdatedAt())
                .latestExecution(latestExecution)
                .listStatus(listStatus)
                .summary(WorkflowListSummaryResponse.from(workflow))
                .readiness(WorkflowListReadinessResponse.from(workflow))
                .warnings(warnings)
                .build();
    }

    public static WorkflowListItemResponse from(
            WorkflowListProjection workflow,
            ExecutionSummaryResponse latestExecution,
            String listStatus,
            List<ValidationWarning> warnings
    ) {
        TriggerConfig normalizedTrigger = WorkflowTriggerSupport.normalizeTrigger(workflow.getTrigger());
        return WorkflowListItemResponse.builder()
                .id(workflow.getId())
                .name(workflow.getName())
                .description(workflow.getDescription())
                .userId(workflow.getUserId())
                .sharedWith(workflow.getSharedWith())
                .template(workflow.isTemplate())
                .templateId(workflow.getTemplateId())
                .active(WorkflowTriggerSupport.normalizeActive(normalizedTrigger, workflow.isActive()))
                .trigger(normalizedTrigger)
                .createdAt(workflow.getCreatedAt())
                .updatedAt(workflow.getUpdatedAt())
                .latestExecution(latestExecution)
                .listStatus(listStatus)
                .summary(WorkflowListSummaryResponse.fromNodes(workflow.getNodes()))
                .readiness(WorkflowListReadinessResponse.fromNodes(workflow.getNodes()))
                .warnings(warnings)
                .build();
    }
}
