package org.github.flowify.workflow.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
@AllArgsConstructor
public class WorkflowGenerationResultResponse {

    private final WorkflowResponse workflow;
    private final String assistantMessage;
    private final WorkflowGenerationStatus status;
    private final boolean requiresUserAction;
    private final List<WorkflowGenerationNextAction> nextActions;
}
