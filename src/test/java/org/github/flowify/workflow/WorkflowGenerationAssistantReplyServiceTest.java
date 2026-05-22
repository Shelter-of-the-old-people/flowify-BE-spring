package org.github.flowify.workflow;

import org.github.flowify.execution.service.FastApiClient;
import org.github.flowify.workflow.dto.WorkflowGenerationAssistantMessageFormat;
import org.github.flowify.workflow.dto.WorkflowGenerationAssistantMessageResponse;
import org.github.flowify.workflow.dto.WorkflowGenerationAssistantMessageType;
import org.github.flowify.workflow.dto.WorkflowGenerationNextAction;
import org.github.flowify.workflow.dto.WorkflowGenerationResultResponse;
import org.github.flowify.workflow.dto.WorkflowGenerationStatus;
import org.github.flowify.workflow.service.generation.WorkflowGenerationAssistantReplyService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WorkflowGenerationAssistantReplyServiceTest {

    @Test
    void enrichGenerated_replacesOnlyAssistantMessageWhenReplyExists() {
        FastApiClient fastApiClient = mock(FastApiClient.class);
        WorkflowGenerationAssistantReplyService service = new WorkflowGenerationAssistantReplyService(fastApiClient);
        WorkflowGenerationResultResponse fallback = fallbackResult();
        ArgumentCaptor<Map<String, Object>> requestCaptor = ArgumentCaptor.forClass(Map.class);

        when(fastApiClient.generateWorkflowAssistantMessage(eq("user1"), requestCaptor.capture()))
                .thenReturn(Optional.of("자연스러운 답변"));

        WorkflowGenerationResultResponse result = service.enrichGenerated(
                "user1",
                "Gmail 요약해서 Discord로 보내줘",
                fallback
        );

        assertThat(result.getAssistantMessage()).isEqualTo("자연스러운 답변");
        assertThat(result.getAssistantMessageFormat()).isEqualTo(WorkflowGenerationAssistantMessageFormat.NATURAL);
        assertThat(result.getStatus()).isEqualTo(fallback.getStatus());
        assertThat(result.getAssistantMessages()).isSameAs(fallback.getAssistantMessages());

        Map<String, Object> requestBody = requestCaptor.getValue();
        assertThat(requestBody)
                .containsEntry("mode", "GENERATED")
                .containsEntry("status", "NEEDS_CONFIGURATION")
                .containsEntry("fallback_message", "Fallback message")
                .containsEntry("requires_user_action", true);
        assertThat(requestBody.get("next_actions")).isEqualTo(List.of("REVIEW_WORKFLOW", "CONFIGURE_NODES"));
        assertThat(requestBody.get("plan")).isEqualTo(Map.of("summary", "Gmail -> AI -> Discord"));
    }

    @Test
    void enrichGenerated_keepsFallbackWhenReplyIsMissing() {
        FastApiClient fastApiClient = mock(FastApiClient.class);
        WorkflowGenerationAssistantReplyService service = new WorkflowGenerationAssistantReplyService(fastApiClient);
        WorkflowGenerationResultResponse fallback = fallbackResult();

        when(fastApiClient.generateWorkflowAssistantMessage(eq("user1"), org.mockito.ArgumentMatchers.anyMap()))
                .thenReturn(Optional.empty());

        WorkflowGenerationResultResponse result = service.enrichGenerated(
                "user1",
                "Gmail 요약해서 Discord로 보내줘",
                fallback
        );

        assertThat(result).isSameAs(fallback);
        assertThat(result.getAssistantMessageFormat()).isEqualTo(WorkflowGenerationAssistantMessageFormat.STRUCTURED);
    }

    private WorkflowGenerationResultResponse fallbackResult() {
        return WorkflowGenerationResultResponse.builder()
                .assistantMessage("Fallback message")
                .assistantMessages(List.of(WorkflowGenerationAssistantMessageResponse.builder()
                        .type(WorkflowGenerationAssistantMessageType.CONFIGURATION_GUIDE)
                        .title("설정 확인")
                        .content("Discord 설정이 필요합니다.")
                        .items(List.of("Discord"))
                        .build()))
                .assistantMessageFormat(WorkflowGenerationAssistantMessageFormat.STRUCTURED)
                .status(WorkflowGenerationStatus.NEEDS_CONFIGURATION)
                .requiresUserAction(true)
                .nextActions(List.of(
                        WorkflowGenerationNextAction.REVIEW_WORKFLOW,
                        WorkflowGenerationNextAction.CONFIGURE_NODES
                ))
                .builderState(Map.of("original_prompt", "Gmail 요약"))
                .plan(Map.of("summary", "Gmail -> AI -> Discord"))
                .build();
    }
}
