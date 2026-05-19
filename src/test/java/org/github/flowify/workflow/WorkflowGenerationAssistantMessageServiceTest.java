package org.github.flowify.workflow;

import org.github.flowify.workflow.dto.NodeStatusResponse;
import org.github.flowify.workflow.dto.WorkflowGenerationNextAction;
import org.github.flowify.workflow.dto.WorkflowGenerationResultResponse;
import org.github.flowify.workflow.dto.WorkflowGenerationStatus;
import org.github.flowify.workflow.dto.WorkflowResponse;
import org.github.flowify.workflow.entity.NodeDefinition;
import org.github.flowify.workflow.service.generation.WorkflowGenerationAssistantMessageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class WorkflowGenerationAssistantMessageServiceTest {

    private WorkflowGenerationAssistantMessageService service;

    @BeforeEach
    void setUp() {
        service = new WorkflowGenerationAssistantMessageService();
    }

    @Test
    void buildResult_returnsGeneratedWhenNoNodeNeedsConfiguration() {
        WorkflowResponse workflow = workflow(List.of(
                        node("gmail", "Gmail"),
                        node("discord", "Discord")
                ),
                List.of(
                        status("gmail", true, List.of()),
                        status("discord", true, List.of())
                ));

        WorkflowGenerationResultResponse result = service.buildResult(workflow);

        assertThat(result.getWorkflow()).isSameAs(workflow);
        assertThat(result.getStatus()).isEqualTo(WorkflowGenerationStatus.GENERATED);
        assertThat(result.isRequiresUserAction()).isFalse();
        assertThat(result.getNextActions()).containsExactly(WorkflowGenerationNextAction.REVIEW_WORKFLOW);
        assertThat(result.getAssistantMessage()).contains("워크플로우 초안");
        assertThat(result.getAssistantMessage()).contains("검토");
    }

    @Test
    void buildResult_returnsNeedsConfigurationWhenNodeNeedsConfiguration() {
        WorkflowResponse workflow = workflow(List.of(
                        node("gmail", "Gmail"),
                        node("discord", "Discord")
                ),
                List.of(
                        status("gmail", true, List.of()),
                        status("discord", false, List.of("config.webhook_url"))
                ));

        WorkflowGenerationResultResponse result = service.buildResult(workflow);

        assertThat(result.getStatus()).isEqualTo(WorkflowGenerationStatus.NEEDS_CONFIGURATION);
        assertThat(result.isRequiresUserAction()).isTrue();
        assertThat(result.getNextActions()).containsExactly(
                WorkflowGenerationNextAction.REVIEW_WORKFLOW,
                WorkflowGenerationNextAction.CONFIGURE_NODES
        );
        assertThat(result.getAssistantMessage()).contains("Discord 설정");
    }

    @Test
    void buildResult_doesNotExposeMissingFieldKeys() {
        WorkflowResponse workflow = workflow(List.of(node("discord", "Discord")),
                List.of(status("discord", false, List.of("config.webhook_url"))));

        WorkflowGenerationResultResponse result = service.buildResult(workflow);

        assertThat(result.getAssistantMessage()).doesNotContain("config.webhook_url");
        assertThat(result.getAssistantMessage()).doesNotContain("webhook_url");
    }

    @Test
    void buildResult_abbreviatesLongNodeNameList() {
        WorkflowResponse workflow = workflow(List.of(
                        node("gmail", "Gmail"),
                        node("ai", "내용 요약"),
                        node("drive", "Google Drive"),
                        node("discord", "Discord")
                ),
                List.of(
                        status("gmail", false, List.of("config.target")),
                        status("ai", false, List.of("config.prompt")),
                        status("drive", false, List.of("config.folder_id")),
                        status("discord", false, List.of("config.webhook_url"))
                ));

        WorkflowGenerationResultResponse result = service.buildResult(workflow);

        assertThat(result.getAssistantMessage()).contains("Gmail, 내용 요약, Google Drive 외 1개");
    }

    @Test
    void buildResult_handlesUnmatchedNodeStatus() {
        WorkflowResponse workflow = workflow(List.of(node("gmail", "Gmail")),
                List.of(status("unknown", false, List.of("config.target"))));

        WorkflowGenerationResultResponse result = service.buildResult(workflow);

        assertThat(result.getStatus()).isEqualTo(WorkflowGenerationStatus.NEEDS_CONFIGURATION);
        assertThat(result.getAssistantMessage()).contains("설정이 필요한 노드");
    }

    private WorkflowResponse workflow(List<NodeDefinition> nodes, List<NodeStatusResponse> nodeStatuses) {
        return WorkflowResponse.builder()
                .id("wf-1")
                .name("테스트 워크플로우")
                .description("")
                .nodes(nodes)
                .edges(List.of())
                .nodeStatuses(nodeStatuses)
                .build();
    }

    private NodeDefinition node(String id, String label) {
        return NodeDefinition.builder()
                .id(id)
                .label(label)
                .type(id)
                .build();
    }

    private NodeStatusResponse status(String nodeId, boolean configured, List<String> missingFields) {
        return NodeStatusResponse.builder()
                .nodeId(nodeId)
                .configured(configured)
                .saveable(true)
                .choiceable(false)
                .executable(configured)
                .missingFields(missingFields)
                .build();
    }
}
