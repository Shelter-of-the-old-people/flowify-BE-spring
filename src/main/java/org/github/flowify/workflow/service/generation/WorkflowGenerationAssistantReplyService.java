package org.github.flowify.workflow.service.generation;

import lombok.RequiredArgsConstructor;
import org.github.flowify.execution.service.FastApiClient;
import org.github.flowify.workflow.dto.WorkflowGenerationAssistantMessageFormat;
import org.github.flowify.workflow.dto.WorkflowGenerationAssistantMessageResponse;
import org.github.flowify.workflow.dto.WorkflowGenerationNextAction;
import org.github.flowify.workflow.dto.WorkflowGenerationResultResponse;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class WorkflowGenerationAssistantReplyService {

    private final FastApiClient fastApiClient;

    public WorkflowGenerationResultResponse enrichGenerated(
            String userId,
            String prompt,
            WorkflowGenerationResultResponse fallback
    ) {
        return enrich(userId, prompt, "GENERATED", fallback);
    }

    public WorkflowGenerationResultResponse enrichRefined(
            String userId,
            String prompt,
            WorkflowGenerationResultResponse fallback
    ) {
        return enrich(userId, prompt, "REFINED", fallback);
    }

    public WorkflowGenerationResultResponse enrichClarification(
            String userId,
            String prompt,
            WorkflowGenerationResultResponse fallback
    ) {
        return enrich(userId, prompt, "CLARIFICATION", fallback);
    }

    private WorkflowGenerationResultResponse enrich(
            String userId,
            String prompt,
            String mode,
            WorkflowGenerationResultResponse fallback
    ) {
        if (fallback == null) {
            return null;
        }

        Map<String, Object> requestBody = buildRequestBody(prompt, mode, fallback);

        // Assistant replies are optional UX text; workflow generation must keep the fallback result when this fails.
        return fastApiClient.generateWorkflowAssistantMessage(userId, requestBody)
                .map(assistantMessage -> fallback.toBuilder()
                        .assistantMessage(assistantMessage)
                        .assistantMessageFormat(WorkflowGenerationAssistantMessageFormat.NATURAL)
                        .build())
                .orElse(fallback);
    }

    private Map<String, Object> buildRequestBody(
            String prompt,
            String mode,
            WorkflowGenerationResultResponse fallback
    ) {
        Map<String, Object> requestBody = new LinkedHashMap<>();
        requestBody.put("prompt", prompt);
        requestBody.put("mode", mode);
        requestBody.put("status", fallback.getStatus().name());
        requestBody.put("fallback_message", fallback.getAssistantMessage());
        requestBody.put("structured_messages", structuredMessages(fallback.getAssistantMessages()));
        requestBody.put("requires_user_action", fallback.isRequiresUserAction());
        requestBody.put("next_actions", nextActions(fallback.getNextActions()));
        requestBody.put("builder_state", fallback.getBuilderState());
        requestBody.put("plan", fallback.getPlan());
        return requestBody;
    }

    private List<Map<String, Object>> structuredMessages(
            List<WorkflowGenerationAssistantMessageResponse> assistantMessages
    ) {
        if (assistantMessages == null) {
            return List.of();
        }

        return assistantMessages.stream()
                .map(message -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("type", message.getType().name());
                    item.put("title", message.getTitle());
                    item.put("content", message.getContent());
                    item.put("items", message.getItems() == null ? List.of() : message.getItems());
                    return item;
                })
                .toList();
    }

    private List<String> nextActions(List<WorkflowGenerationNextAction> nextActions) {
        if (nextActions == null) {
            return List.of();
        }
        return nextActions.stream()
                .map(Enum::name)
                .toList();
    }
}
