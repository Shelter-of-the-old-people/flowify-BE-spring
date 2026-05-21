package org.github.flowify.workflow.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.util.List;
import java.util.Map;

@Getter
@Builder(toBuilder = true)
@AllArgsConstructor
public class WorkflowGenerationResultResponse {

    private final WorkflowResponse workflow;
    private final String assistantMessage;
    private final List<WorkflowGenerationAssistantMessageResponse> assistantMessages;
    private final WorkflowGenerationAssistantMessageFormat assistantMessageFormat;
    private final WorkflowGenerationClarificationResponse clarification;
    private final WorkflowGenerationStatus status;
    private final boolean requiresUserAction;
    private final List<WorkflowGenerationNextAction> nextActions;
    private final Map<String, Object> builderState;
    private final Map<String, Object> plan;
}
