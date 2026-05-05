package org.github.flowify.workflow;

import org.github.flowify.catalog.service.NodeLifecycleService;
import org.github.flowify.common.exception.BusinessException;
import org.github.flowify.common.exception.ErrorCode;
import org.github.flowify.workflow.dto.NodePreviewResponse;
import org.github.flowify.workflow.dto.NodeStatusResponse;
import org.github.flowify.workflow.entity.NodeDefinition;
import org.github.flowify.workflow.entity.Workflow;
import org.github.flowify.workflow.service.WorkflowPreviewService;
import org.github.flowify.workflow.service.WorkflowService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorkflowPreviewServiceTest {

    @Mock
    private WorkflowService workflowService;
    @Mock
    private NodeLifecycleService nodeLifecycleService;

    @InjectMocks
    private WorkflowPreviewService workflowPreviewService;

    private Workflow workflow;
    private NodeDefinition node;

    @BeforeEach
    void setUp() {
        node = NodeDefinition.builder()
                .id("node_1")
                .role("start")
                .type("google_drive")
                .outputDataType("FILE_LIST")
                .build();

        workflow = Workflow.builder()
                .id("wf1")
                .userId("user123")
                .nodes(new ArrayList<>(List.of(node)))
                .edges(new ArrayList<>())
                .sharedWith(new ArrayList<>())
                .build();
    }

    @Test
    @DisplayName("노드 미리보기 - 설정 미완료")
    void previewNode_unavailableWhenNodeNotReady() {
        when(workflowService.findWorkflowOrThrow("wf1")).thenReturn(workflow);
        when(nodeLifecycleService.evaluate(node, "user123")).thenReturn(NodeStatusResponse.builder()
                .nodeId("node_1")
                .configured(false)
                .executable(false)
                .missingFields(List.of("config.target"))
                .build());

        NodePreviewResponse response = workflowPreviewService.previewNode("user123", "wf1", "node_1", null);

        assertThat(response.isAvailable()).isFalse();
        assertThat(response.getStatus()).isEqualTo("unavailable");
        assertThat(response.getReason()).isEqualTo("NODE_NOT_CONFIGURED");
        assertThat(response.getMissingFields()).containsExactly("config.target");
        assertThat(response.getMetadata()).containsEntry("limit", 5);
    }

    @Test
    @DisplayName("노드 미리보기 - 준비 완료 shell 응답")
    void previewNode_readyReturnsShellResponse() {
        when(workflowService.findWorkflowOrThrow("wf1")).thenReturn(workflow);
        when(nodeLifecycleService.evaluate(node, "user123")).thenReturn(NodeStatusResponse.builder()
                .nodeId("node_1")
                .configured(true)
                .executable(true)
                .build());

        NodePreviewResponse response = workflowPreviewService.previewNode("user123", "wf1", "node_1", null);

        assertThat(response.isAvailable()).isFalse();
        assertThat(response.getStatus()).isEqualTo("unavailable");
        assertThat(response.getReason()).isEqualTo("PREVIEW_NOT_IMPLEMENTED");
        assertThat(response.getMetadata()).containsEntry("nodeType", "google_drive");
    }

    @Test
    @DisplayName("노드 미리보기 - 소유자만 허용")
    void previewNode_ownerOnly() {
        workflow.setSharedWith(List.of("other-user"));
        when(workflowService.findWorkflowOrThrow("wf1")).thenReturn(workflow);

        assertThatThrownBy(() -> workflowPreviewService.previewNode("other-user", "wf1", "node_1", null))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.WORKFLOW_ACCESS_DENIED);
    }
}
