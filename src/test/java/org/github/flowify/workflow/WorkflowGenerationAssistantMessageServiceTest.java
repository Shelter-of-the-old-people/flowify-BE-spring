package org.github.flowify.workflow;

import org.github.flowify.catalog.dto.SinkCatalog;
import org.github.flowify.catalog.dto.SinkService;
import org.github.flowify.catalog.dto.SourceCatalog;
import org.github.flowify.catalog.dto.SourceService;
import org.github.flowify.catalog.service.CatalogService;
import org.github.flowify.workflow.dto.NodeStatusResponse;
import org.github.flowify.workflow.dto.WorkflowGenerationAssistantMessageResponse;
import org.github.flowify.workflow.dto.WorkflowGenerationAssistantMessageType;
import org.github.flowify.workflow.dto.WorkflowGenerationNextAction;
import org.github.flowify.workflow.dto.WorkflowGenerationResultResponse;
import org.github.flowify.workflow.dto.WorkflowGenerationStatus;
import org.github.flowify.workflow.dto.WorkflowResponse;
import org.github.flowify.workflow.entity.EdgeDefinition;
import org.github.flowify.workflow.entity.NodeDefinition;
import org.github.flowify.workflow.service.generation.WorkflowGenerationAssistantMessageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WorkflowGenerationAssistantMessageServiceTest {

    private WorkflowGenerationAssistantMessageService service;

    @BeforeEach
    void setUp() {
        CatalogService catalogService = mock(CatalogService.class);
        when(catalogService.getSourceCatalog()).thenReturn(sourceCatalog());
        when(catalogService.getSinkCatalog()).thenReturn(sinkCatalog());
        service = new WorkflowGenerationAssistantMessageService(catalogService);
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
    void buildRefinedResult_usesRefinedMessagePrefix() {
        WorkflowResponse workflow = workflow(List.of(
                        node("gmail", "Gmail"),
                        node("discord", "Discord")
                ),
                List.of(
                        status("gmail", true, List.of()),
                        status("discord", true, List.of())
                ));

        WorkflowGenerationResultResponse result = service.buildRefinedResult(workflow);

        assertThat(result.getStatus()).isEqualTo(WorkflowGenerationStatus.GENERATED);
        assertThat(result.getAssistantMessage()).contains("요청한 내용을 반영했어요");
        assertThat(result.getAssistantMessage()).doesNotContain("워크플로우 초안");
    }

    @Test
    void buildGeneratedResult_includesWorkflowPathSummary() {
        WorkflowResponse workflow = workflow(
                List.of(
                        node("gmail", "gmail", "New Email", "start", Map.of("service", "gmail")),
                        node("ai", "AI", "내용 요약", "middle", Map.of("choiceActionId", "summarize")),
                        node("discord", "discord", "Send to Discord", "end", Map.of("service", "discord"))
                ),
                List.of(
                        edge("gmail", "ai"),
                        edge("ai", "discord")
                ),
                List.of(
                        status("gmail", true, List.of()),
                        status("ai", true, List.of()),
                        status("discord", true, List.of())
                )
        );

        WorkflowGenerationResultResponse result = service.buildGeneratedResult(workflow);

        assertThat(result.getAssistantMessage())
                .contains("Gmail → 내용 요약 → Discord 흐름으로 구성했습니다.");
        assertThat(result.getAssistantMessage()).contains("화면에서 흐름을 검토");
    }

    @Test
    void buildRefinedResult_includesRefinedWorkflowPathSummary() {
        WorkflowResponse workflow = workflow(
                List.of(
                        node("github", "github", "New Pull Request", "start", Map.of("service", "github")),
                        node("ai", "AI", "내용 요약", "middle", Map.of("choiceActionId", "summarize")),
                        node("discord", "discord", "Send to Discord", "end", Map.of("service", "discord"))
                ),
                List.of(
                        edge("github", "ai"),
                        edge("ai", "discord")
                ),
                List.of(
                        status("github", true, List.of()),
                        status("ai", true, List.of()),
                        status("discord", false, List.of("config.webhook_url"))
                )
        );

        WorkflowGenerationResultResponse result = service.buildRefinedResult(workflow);

        assertThat(result.getAssistantMessage())
                .contains("GitHub → 내용 요약 → Discord 흐름으로 정리했습니다.");
        assertThat(result.getAssistantMessage()).contains("Discord 설정을 확인하면 실행할 수 있습니다.");
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
    void buildResult_usesSinkCatalogLabelForEndNode() {
        WorkflowResponse workflow = workflow(List.of(
                        node("discord", "discord", "Send to Discord", "end", Map.of("service", "discord"))
                ),
                List.of(status("discord", false, List.of("config.webhook_url"))));

        WorkflowGenerationResultResponse result = service.buildResult(workflow);

        assertThat(result.getAssistantMessage()).contains("Discord");
        assertThat(result.getAssistantMessage()).doesNotContain("Send to Discord");
    }

    @Test
    void buildResult_usesSourceCatalogLabelForStartNode() {
        WorkflowResponse workflow = workflow(List.of(
                        node("gmail", "gmail", "New Email", "start", Map.of("service", "gmail"))
                ),
                List.of(status("gmail", false, List.of("config.target"))));

        WorkflowGenerationResultResponse result = service.buildResult(workflow);

        assertThat(result.getAssistantMessage()).contains("Gmail");
        assertThat(result.getAssistantMessage()).doesNotContain("New Email");
    }

    @Test
    void buildResult_keepsMiddleNodeLabel() {
        WorkflowResponse workflow = workflow(List.of(
                        node("middle", "discord", "Custom Middle", "middle", Map.of("service", "discord"))
                ),
                List.of(status("middle", false, List.of("config.prompt"))));

        WorkflowGenerationResultResponse result = service.buildResult(workflow);

        assertThat(result.getAssistantMessage()).contains("Custom Middle");
        assertThat(result.getAssistantMessage()).doesNotContain("Discord");
    }

    @Test
    void buildResult_fallsBackToNodeLabelWhenCatalogServiceIsUnknown() {
        WorkflowResponse workflow = workflow(List.of(
                        node("custom", "unknown_sink", "Custom Sink", "end", Map.of("service", "unknown_sink"))
                ),
                List.of(status("custom", false, List.of("config.target"))));

        WorkflowGenerationResultResponse result = service.buildResult(workflow);

        assertThat(result.getAssistantMessage()).contains("Custom Sink");
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

    @Test
    void buildResult_omitsWorkflowPathSummaryWhenPathCannotBeResolved() {
        WorkflowResponse workflow = workflow(
                List.of(
                        node("gmail", "gmail", "New Email", "start", Map.of("service", "gmail")),
                        node("ai", "AI", "내용 요약", "middle", Map.of("choiceActionId", "summarize")),
                        node("discord", "discord", "Send to Discord", "end", Map.of("service", "discord"))
                ),
                List.of(
                        edge("gmail", "ai"),
                        edge("gmail", "discord")
                ),
                List.of(
                        status("gmail", true, List.of()),
                        status("ai", true, List.of()),
                        status("discord", true, List.of())
                )
        );

        WorkflowGenerationResultResponse result = service.buildResult(workflow);

        assertThat(result.getAssistantMessage()).contains("워크플로우 초안");
        assertThat(result.getAssistantMessage()).contains("화면에서 흐름을 검토");
        assertThat(result.getAssistantMessage()).doesNotContain("→");
    }

    @Test
    void buildGeneratedResult_includesStructuredAssistantMessages() {
        WorkflowResponse workflow = workflow(
                List.of(
                        node("gmail", "gmail", "New Email", "start", Map.of("service", "gmail")),
                        node("ai", "AI", "Summary", "middle", Map.of("choiceActionId", "summarize")),
                        node("discord", "discord", "Send to Discord", "end", Map.of("service", "discord"))
                ),
                List.of(
                        edge("gmail", "ai"),
                        edge("ai", "discord")
                ),
                List.of(
                        status("gmail", true, List.of()),
                        status("ai", true, List.of()),
                        status("discord", true, List.of())
                )
        );

        WorkflowGenerationResultResponse result = service.buildGeneratedResult(workflow);

        assertThat(result.getAssistantMessages())
                .extracting(WorkflowGenerationAssistantMessageResponse::getType)
                .containsExactly(
                        WorkflowGenerationAssistantMessageType.SUMMARY,
                        WorkflowGenerationAssistantMessageType.WORKFLOW_FLOW,
                        WorkflowGenerationAssistantMessageType.NEXT_STEP
                );
        assertThat(result.getAssistantMessages().get(1).getContent())
                .contains("Gmail")
                .contains("Discord");
        assertThat(result.getAssistantMessages().get(1).getItems())
                .contains("Gmail", "Discord");
    }

    @Test
    void buildResult_includesConfigurationGuideAssistantMessage() {
        WorkflowResponse workflow = workflow(
                List.of(
                        node("gmail", "gmail", "New Email", "start", Map.of("service", "gmail")),
                        node("discord", "discord", "Send to Discord", "end", Map.of("service", "discord"))
                ),
                List.of(edge("gmail", "discord")),
                List.of(
                        status("gmail", true, List.of()),
                        status("discord", false, List.of("config.webhook_url"))
                )
        );

        WorkflowGenerationResultResponse result = service.buildResult(workflow);
        WorkflowGenerationAssistantMessageResponse configurationGuide = result.getAssistantMessages().stream()
                .filter(message -> message.getType() == WorkflowGenerationAssistantMessageType.CONFIGURATION_GUIDE)
                .findFirst()
                .orElseThrow();

        assertThat(configurationGuide.getContent())
                .contains("Discord")
                .doesNotContain("config.webhook_url")
                .doesNotContain("webhook_url");
        assertThat(configurationGuide.getItems()).containsExactly("Discord");
    }

    private WorkflowResponse workflow(List<NodeDefinition> nodes, List<NodeStatusResponse> nodeStatuses) {
        return workflow(nodes, List.of(), nodeStatuses);
    }

    private WorkflowResponse workflow(
            List<NodeDefinition> nodes,
            List<EdgeDefinition> edges,
            List<NodeStatusResponse> nodeStatuses
    ) {
        return WorkflowResponse.builder()
                .id("wf-1")
                .name("테스트 워크플로우")
                .description("")
                .nodes(nodes)
                .edges(edges)
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

    private NodeDefinition node(String id, String type, String label, String role, Map<String, Object> config) {
        return NodeDefinition.builder()
                .id(id)
                .type(type)
                .label(label)
                .role(role)
                .config(config)
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

    private EdgeDefinition edge(String source, String target) {
        return EdgeDefinition.builder()
                .id("edge_" + source + "_" + target)
                .source(source)
                .target(target)
                .build();
    }

    private SourceCatalog sourceCatalog() {
        return new SourceCatalog(
                new SourceCatalog.Meta("test", "2026-05-20"),
                List.of(
                        new SourceService("gmail", "Gmail", true, List.of()),
                        new SourceService("github", "GitHub", true, List.of())
                )
        );
    }

    private SinkCatalog sinkCatalog() {
        return new SinkCatalog(
                new SourceCatalog.Meta("test", "2026-05-20"),
                List.of(
                        new SinkService("discord", "Discord", false, List.of("TEXT"), "per_service", Map.of()),
                        new SinkService("google_drive", "Google Drive", true, List.of("TEXT"), "per_service", Map.of())
                )
        );
    }
}
