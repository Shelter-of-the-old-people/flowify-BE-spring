package org.github.flowify.workflow;

import org.github.flowify.catalog.service.CatalogService;
import org.github.flowify.catalog.service.NodeLifecycleService;
import org.github.flowify.common.exception.BusinessException;
import org.github.flowify.common.exception.ErrorCode;
import org.github.flowify.execution.service.FastApiClient;
import org.github.flowify.execution.service.WorkflowTranslator;
import org.github.flowify.oauth.service.OAuthTokenService;
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
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorkflowPreviewServiceTest {

    @Mock
    private WorkflowService workflowService;
    @Mock
    private NodeLifecycleService nodeLifecycleService;
    @Mock
    private FastApiClient fastApiClient;
    @Mock
    private WorkflowTranslator workflowTranslator;
    @Mock
    private OAuthTokenService oauthTokenService;
    @Mock
    private CatalogService catalogService;

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
    @DisplayName("노드 미리보기 - 준비 완료 FastAPI 호출")
    void previewNode_readyCallsFastApi() {
        when(workflowService.findWorkflowOrThrow("wf1")).thenReturn(workflow);
        when(nodeLifecycleService.evaluate(node, "user123")).thenReturn(NodeStatusResponse.builder()
                .nodeId("node_1")
                .configured(true)
                .executable(true)
                .build());
        when(catalogService.isAuthRequired("google_drive")).thenReturn(false);
        when(workflowTranslator.toRuntimeModel(workflow)).thenReturn(Map.of("id", "wf1"));
        when(fastApiClient.previewNode(
                "wf1", "user123", "node_1", Map.of("id", "wf1"), Map.of(), 5, false))
                .thenReturn(NodePreviewResponse.builder()
                        .workflowId("wf1")
                        .nodeId("node_1")
                        .status("available")
                        .available(true)
                        .outputData(Map.of("type", "FILE_LIST"))
                        .build());

        NodePreviewResponse response = workflowPreviewService.previewNode("user123", "wf1", "node_1", null);

        assertThat(response.isAvailable()).isTrue();
        assertThat(response.getStatus()).isEqualTo("available");
        assertThat(response.getOutputData()).isEqualTo(Map.of("type", "FILE_LIST"));
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

    @Test
    @DisplayName("노드 미리보기 - source token만 수집")
    void previewNode_sourcePreviewCollectsOnlyTargetSourceToken() {
        NodeDefinition sinkNode = NodeDefinition.builder()
                .id("node_sink")
                .role("end")
                .type("gmail")
                .build();
        workflow.setNodes(new ArrayList<>(List.of(node, sinkNode)));

        when(workflowService.findWorkflowOrThrow("wf1")).thenReturn(workflow);
        when(nodeLifecycleService.evaluate(node, "user123")).thenReturn(NodeStatusResponse.builder()
                .nodeId("node_1")
                .configured(true)
                .executable(true)
                .build());
        when(catalogService.isAuthRequired("google_drive")).thenReturn(true);
        when(oauthTokenService.getDecryptedToken("user123", "google_drive")).thenReturn("drive-token");
        when(workflowTranslator.toRuntimeModel(workflow)).thenReturn(Map.of("id", "wf1"));
        when(fastApiClient.previewNode(
                "wf1", "user123", "node_1", Map.of("id", "wf1"), Map.of("google_drive", "drive-token"), 5, false))
                .thenReturn(NodePreviewResponse.builder()
                        .workflowId("wf1")
                        .nodeId("node_1")
                        .status("available")
                        .available(true)
                        .outputData(Map.of("type", "FILE_LIST"))
                        .build());

        NodePreviewResponse response = workflowPreviewService.previewNode("user123", "wf1", "node_1", null);

        assertThat(response.isAvailable()).isTrue();
        verify(oauthTokenService).getDecryptedToken("user123", "google_drive");
        verify(oauthTokenService, never()).getDecryptedToken("user123", "gmail");
        verify(catalogService, never()).isAuthRequired("gmail");
    }

    @Test
    @DisplayName("노드 미리보기 - 미지원 노드는 token 검사 전 차단")
    void previewNode_unsupportedNodeReturnsNotImplementedBeforeLifecycle() {
        NodeDefinition sinkNode = NodeDefinition.builder()
                .id("node_sink")
                .role("end")
                .type("google_drive")
                .build();
        workflow.setNodes(new ArrayList<>(List.of(sinkNode)));
        when(workflowService.findWorkflowOrThrow("wf1")).thenReturn(workflow);

        NodePreviewResponse response = workflowPreviewService.previewNode("user123", "wf1", "node_sink", null);

        assertThat(response.isAvailable()).isFalse();
        assertThat(response.getStatus()).isEqualTo("unavailable");
        assertThat(response.getReason()).isEqualTo("PREVIEW_NOT_IMPLEMENTED");
        verify(nodeLifecycleService, never()).evaluate(any(NodeDefinition.class), anyString());
        verify(oauthTokenService, never()).getDecryptedToken(anyString(), anyString());
    }
}
