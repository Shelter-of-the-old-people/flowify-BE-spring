package org.github.flowify.workflow.dto;

import org.github.flowify.workflow.entity.EdgeDefinition;
import org.github.flowify.workflow.entity.NodeDefinition;
import org.github.flowify.workflow.entity.TriggerConfig;

import java.time.Instant;
import java.util.List;

public interface WorkflowListProjection {

    String getId();

    String getName();

    String getDescription();

    String getUserId();

    List<String> getSharedWith();

    boolean isTemplate();

    String getTemplateId();

    boolean isActive();

    TriggerConfig getTrigger();

    Instant getCreatedAt();

    Instant getUpdatedAt();

    String getLatestExecutionId();

    String getLatestExecutionState();

    Instant getLatestExecutionStartedAt();

    Instant getLatestExecutionFinishedAt();

    List<NodeDefinition> getNodes();

    List<EdgeDefinition> getEdges();
}
