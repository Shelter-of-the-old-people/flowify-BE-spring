package org.github.flowify.workflow;

import org.github.flowify.catalog.service.CatalogService;
import org.github.flowify.catalog.service.NodeLifecycleService;
import org.github.flowify.common.exception.BusinessException;
import org.github.flowify.common.exception.ErrorCode;
import org.github.flowify.execution.service.FastApiClient;
import org.github.flowify.execution.service.RuntimeContextService;
import org.github.flowify.execution.service.WorkflowTranslator;
import org.github.flowify.oauth.service.OAuthTokenService;
import org.github.flowify.workflow.dto.NodePreviewRequest;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
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
    @Mock
    private RuntimeContextService runtimeContextService;

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
        assertThat(response.getMetadata())
                .containsEntry("limit", 5)
                .containsEntry("previewScope", "source_metadata")
                .containsEntry("contentPolicy", "metadata_only")
                .containsEntry("contentIncluded", false)
                .containsEntry("contentStatusScope", "none")
                .containsEntry("contentRequired", false);
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
                "wf1", "user123", "node_1", Map.of("id", "wf1"), Map.of(), 5, false, Map.of()))
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
        assertThat(response.getMetadata())
                .containsEntry("previewScope", "source_metadata")
                .containsEntry("contentPolicy", "metadata_only")
                .containsEntry("contentIncluded", false)
                .containsEntry("contentStatusScope", "none");
    }

    @Test
    @DisplayName("노드 미리보기는 FastAPI runtime_context에 사용자 표시명을 포함한다")
    void previewNode_includesUserDisplayNameInRuntimeContext() {
        when(workflowService.findWorkflowOrThrow("wf1")).thenReturn(workflow);
        when(nodeLifecycleService.evaluate(node, "user123")).thenReturn(NodeStatusResponse.builder()
                .nodeId("node_1")
                .configured(true)
                .executable(true)
                .build());
        when(catalogService.isAuthRequired("google_drive")).thenReturn(false);
        when(workflowTranslator.toRuntimeModel(workflow)).thenReturn(Map.of("id", "wf1"));
        when(runtimeContextService.buildForUser("user123")).thenReturn(Map.of(
                "user_profile", Map.of(
                        "user_id", "user123",
                        "email", "user123@example.com",
                        "display_name", "김민호"
                )
        ));
        when(fastApiClient.previewNode(
                eq("wf1"),
                eq("user123"),
                eq("node_1"),
                eq(Map.of("id", "wf1")),
                eq(Map.of()),
                eq(5),
                eq(false),
                any()))
                .thenReturn(NodePreviewResponse.builder()
                        .workflowId("wf1")
                        .nodeId("node_1")
                        .status("available")
                        .available(true)
                        .outputData(Map.of("type", "FILE_LIST"))
                        .build());

        workflowPreviewService.previewNode("user123", "wf1", "node_1", null);

        @SuppressWarnings("unchecked")
        org.mockito.ArgumentCaptor<Map<String, Object>> runtimeContextCaptor =
                org.mockito.ArgumentCaptor.forClass(Map.class);
        verify(fastApiClient).previewNode(
                eq("wf1"),
                eq("user123"),
                eq("node_1"),
                eq(Map.of("id", "wf1")),
                eq(Map.of()),
                eq(5),
                eq(false),
                runtimeContextCaptor.capture()
        );
        assertThat(runtimeContextCaptor.getValue()).isEqualTo(Map.of(
                "user_profile", Map.of(
                        "user_id", "user123",
                        "email", "user123@example.com",
                        "display_name", "김민호"
                )
        ));
    }

    @Test
    @DisplayName("노드 미리보기 - includeContent 요청 시 content metadata 기본값 보강")
    void previewNode_includeContentAddsContentIncludedMetadata() {
        when(workflowService.findWorkflowOrThrow("wf1")).thenReturn(workflow);
        when(nodeLifecycleService.evaluate(node, "user123")).thenReturn(NodeStatusResponse.builder()
                .nodeId("node_1")
                .configured(true)
                .executable(true)
                .build());
        when(catalogService.isAuthRequired("google_drive")).thenReturn(false);
        when(workflowTranslator.toRuntimeModel(workflow)).thenReturn(Map.of("id", "wf1"));
        when(fastApiClient.previewNode(
                "wf1", "user123", "node_1", Map.of("id", "wf1"), Map.of(), 5, true, Map.of()))
                .thenReturn(NodePreviewResponse.builder()
                        .workflowId("wf1")
                        .nodeId("node_1")
                        .status("available")
                        .available(true)
                        .outputData(Map.of(
                                "type", "SINGLE_FILE",
                                "content_status", "available"))
                        .metadata(Map.of("contentStatusScope", "item"))
                        .build());

        NodePreviewResponse response = workflowPreviewService.previewNode(
                "user123", "wf1", "node_1", new NodePreviewRequest(null, true));

        assertThat(response.getMetadata())
                .containsEntry("includeContent", true)
                .containsEntry("contentPolicy", "content_included")
                .containsEntry("contentIncluded", true)
                .containsEntry("contentStatusScope", "item");
    }

    @Test
    @DisplayName("노드 미리보기 - includeContent 요청이어도 실제 본문이 없으면 content_included로 표시하지 않음")
    void previewNode_includeContentWithoutPayloadContentIsNotMarkedIncluded() {
        when(workflowService.findWorkflowOrThrow("wf1")).thenReturn(workflow);
        when(nodeLifecycleService.evaluate(node, "user123")).thenReturn(NodeStatusResponse.builder()
                .nodeId("node_1")
                .configured(true)
                .executable(true)
                .build());
        when(catalogService.isAuthRequired("google_drive")).thenReturn(false);
        when(workflowTranslator.toRuntimeModel(workflow)).thenReturn(Map.of("id", "wf1"));
        Map<String, Object> fastApiMetadata = new HashMap<>();
        fastApiMetadata.put("contentPolicy", null);
        fastApiMetadata.put("contentIncluded", null);
        when(fastApiClient.previewNode(
                "wf1", "user123", "node_1", Map.of("id", "wf1"), Map.of(), 5, true, Map.of()))
                .thenReturn(NodePreviewResponse.builder()
                        .workflowId("wf1")
                        .nodeId("node_1")
                        .status("available")
                        .available(true)
                        .outputData(Map.of("type", "SINGLE_FILE"))
                        .metadata(fastApiMetadata)
                        .build());

        NodePreviewResponse response = workflowPreviewService.previewNode(
                "user123", "wf1", "node_1", new NodePreviewRequest(null, true));

        assertThat(response.getMetadata())
                .containsEntry("includeContent", true)
                .containsEntry("contentPolicy", "metadata_only")
                .containsEntry("contentIncluded", false);
    }

    @Test
    @DisplayName("노드 미리보기 - includeContent 요청에서 content_status만 있으면 content_status_only로 표시")
    void previewNode_includeContentWithUnsupportedStatusUsesContentStatusOnlyPolicy() {
        when(workflowService.findWorkflowOrThrow("wf1")).thenReturn(workflow);
        when(nodeLifecycleService.evaluate(node, "user123")).thenReturn(NodeStatusResponse.builder()
                .nodeId("node_1")
                .configured(true)
                .executable(true)
                .build());
        when(catalogService.isAuthRequired("google_drive")).thenReturn(false);
        when(workflowTranslator.toRuntimeModel(workflow)).thenReturn(Map.of("id", "wf1"));
        Map<String, Object> contentMetadata = Map.of(
                "extraction_method", "none",
                "content_kind", "none",
                "provider", "openai_vision",
                "limits", Map.of("max_ocr_pages", 10, "max_image_pixels", 12000000)
        );
        when(fastApiClient.previewNode(
                "wf1", "user123", "node_1", Map.of("id", "wf1"), Map.of(), 5, true, Map.of()))
                .thenReturn(NodePreviewResponse.builder()
                        .workflowId("wf1")
                        .nodeId("node_1")
                        .status("available")
                        .available(true)
                        .outputData(Map.of(
                                "type", "SINGLE_FILE",
                                "filename", "scanned.pdf",
                                "content_status", "unsupported",
                                "content_error", "현재 OCR/이미지 분석을 지원하지 않습니다.",
                                "content_metadata", contentMetadata))
                        .build());

        NodePreviewResponse response = workflowPreviewService.previewNode(
                "user123", "wf1", "node_1", new NodePreviewRequest(null, true));

        assertThat(response.getMetadata())
                .containsEntry("includeContent", true)
                .containsEntry("contentPolicy", "content_status_only")
                .containsEntry("contentIncluded", false);
        assertThat(response.getOutputData())
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.MAP)
                .containsEntry("content_metadata", contentMetadata);
    }

    @Test
    @DisplayName("노드 미리보기 - Google Sheets source preview를 FastAPI로 전달")
    void previewNode_googleSheetsReadyCallsFastApi() {
        node = NodeDefinition.builder()
                .id("node_1")
                .role("start")
                .type("google_sheets")
                .outputDataType("SPREADSHEET_DATA")
                .build();
        workflow.setNodes(new ArrayList<>(List.of(node)));

        when(workflowService.findWorkflowOrThrow("wf1")).thenReturn(workflow);
        when(nodeLifecycleService.evaluate(node, "user123")).thenReturn(NodeStatusResponse.builder()
                .nodeId("node_1")
                .configured(true)
                .executable(true)
                .build());
        when(catalogService.isAuthRequired("google_sheets")).thenReturn(true);
        when(oauthTokenService.getDecryptedToken(eq("user123"), eq("google_sheets"), eq(List.of())))
                .thenReturn("sheets-token");
        when(workflowTranslator.toRuntimeModel(workflow)).thenReturn(Map.of("id", "wf1"));
        when(fastApiClient.previewNode(
                "wf1",
                "user123",
                "node_1",
                Map.of("id", "wf1"),
                Map.of("google_sheets", "sheets-token"),
                5,
                false,
                Map.of()))
                .thenReturn(NodePreviewResponse.builder()
                        .workflowId("wf1")
                        .nodeId("node_1")
                        .status("available")
                        .available(true)
                        .outputData(Map.of(
                                "type", "SPREADSHEET_DATA",
                                "headers", List.of("id", "status"),
                                "rows", List.of(List.of("a", "open")),
                                "metadata", Map.of("totalRows", 3, "truncated", true)
                        ))
                        .build());

        NodePreviewResponse response = workflowPreviewService.previewNode("user123", "wf1", "node_1", null);

        assertThat(response.isAvailable()).isTrue();
        assertThat(response.getStatus()).isEqualTo("available");
        @SuppressWarnings("unchecked")
        Map<String, Object> outputData = (Map<String, Object>) response.getOutputData();
        assertThat(outputData).containsEntry("type", "SPREADSHEET_DATA");
        verify(oauthTokenService).getDecryptedToken(eq("user123"), eq("google_sheets"), eq(List.of()));
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
        when(oauthTokenService.getDecryptedToken(eq("user123"), eq("google_drive"), anyList()))
                .thenReturn("drive-token");
        when(workflowTranslator.toRuntimeModel(workflow)).thenReturn(Map.of("id", "wf1"));
        when(fastApiClient.previewNode(
                "wf1", "user123", "node_1", Map.of("id", "wf1"), Map.of("google_drive", "drive-token"), 5, false, Map.of()))
                .thenReturn(NodePreviewResponse.builder()
                        .workflowId("wf1")
                        .nodeId("node_1")
                        .status("available")
                        .available(true)
                        .outputData(Map.of("type", "FILE_LIST"))
                        .build());

        NodePreviewResponse response = workflowPreviewService.previewNode("user123", "wf1", "node_1", null);

        assertThat(response.isAvailable()).isTrue();
        verify(oauthTokenService).getDecryptedToken(eq("user123"), eq("google_drive"), anyList());
        verify(oauthTokenService, never()).getDecryptedToken(eq("user123"), eq("gmail"), anyList());
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
        verify(oauthTokenService, never()).getDecryptedToken(anyString(), anyString(), anyList());
    }
}
