package org.github.flowify.execution;

import com.mongodb.client.result.UpdateResult;
import org.github.flowify.common.exception.BusinessException;
import org.github.flowify.common.exception.ErrorCode;
import org.github.flowify.catalog.service.CatalogService;
import org.github.flowify.catalog.service.NodeLifecycleService;
import org.github.flowify.execution.dto.ExecutionDetailResponse;
import org.github.flowify.execution.dto.ExecutionSummaryResponse;
import org.github.flowify.execution.dto.NodeDataResponse;
import org.github.flowify.execution.entity.ErrorDetail;
import org.github.flowify.execution.entity.NodeLog;
import org.github.flowify.execution.entity.WorkflowExecution;
import org.github.flowify.execution.repository.ExecutionRepository;
import org.github.flowify.execution.service.ExecutionService;
import org.github.flowify.execution.service.FastApiClient;
import org.github.flowify.execution.service.RuntimeContextService;
import org.github.flowify.execution.service.SnapshotService;
import org.github.flowify.execution.service.WorkflowTranslator;
import org.github.flowify.oauth.service.OAuthTokenService;
import org.github.flowify.workflow.entity.NodeDefinition;
import org.github.flowify.workflow.entity.Workflow;
import org.github.flowify.workflow.service.WorkflowService;
import org.github.flowify.workflow.service.WorkflowValidator;
import org.github.flowify.workflow.state.service.WorkflowNodeStateService;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExecutionServiceTest {

    private static final String GMAIL_READONLY_SCOPE = "https://www.googleapis.com/auth/gmail.readonly";
    private static final String GMAIL_SEND_SCOPE = "https://www.googleapis.com/auth/gmail.send";

    @Mock
    private ExecutionRepository executionRepository;
    @Mock
    private WorkflowService workflowService;
    @Mock
    private MongoTemplate mongoTemplate;
    @Mock
    private FastApiClient fastApiClient;
    @Mock
    private OAuthTokenService oauthTokenService;
    @Mock
    private CatalogService catalogService;
    @Mock
    private NodeLifecycleService nodeLifecycleService;
    @Mock
    private SnapshotService snapshotService;
    @Mock
    private WorkflowValidator workflowValidator;
    @Mock
    private WorkflowTranslator workflowTranslator;
    @Mock
    private WorkflowNodeStateService workflowNodeStateService;
    @Mock
    private RuntimeContextService runtimeContextService;

    @InjectMocks
    private ExecutionService executionService;

    private Workflow testWorkflow;
    private WorkflowExecution testExecution;

    @BeforeEach
    void setUp() {
        testWorkflow = Workflow.builder()
                .id("wf1")
                .userId("user123")
                .nodes(new ArrayList<>())
                .edges(new ArrayList<>())
                .sharedWith(new ArrayList<>())
                .build();

        testExecution = WorkflowExecution.builder()
                .id("exec1")
                .workflowId("wf1")
                .userId("user123")
                .state("success")
                .startedAt(Instant.now())
                .build();
    }

    @Test
    @DisplayName("워크플로우 실행 성공")
    void executeWorkflow_success() {
        when(workflowService.findWorkflowOrThrow("wf1")).thenReturn(testWorkflow);
        when(workflowTranslator.toRuntimeModel(testWorkflow)).thenReturn(Map.of());
        when(fastApiClient.execute(eq("wf1"), eq("user123"), any(), anyMap(), anyMap()))
                .thenReturn("exec-123");

        String executionId = executionService.executeWorkflow("user123", "wf1");

        assertThat(executionId).isEqualTo("exec-123");
    }

    @Test
    @DisplayName("워크플로우 실행 시 FastAPI runtime_context에 사용자 표시명을 포함한다")
    void executeWorkflow_includesUserDisplayNameInRuntimeContext() {
        when(workflowService.findWorkflowOrThrow("wf1")).thenReturn(testWorkflow);
        when(workflowTranslator.toRuntimeModel(testWorkflow)).thenReturn(Map.of());
        when(runtimeContextService.buildForUser("user123")).thenReturn(Map.of(
                "user_profile", Map.of(
                        "user_id", "user123",
                        "email", "user123@example.com",
                        "display_name", "김민호"
                )
        ));
        when(fastApiClient.execute(eq("wf1"), eq("user123"), any(), anyMap(), anyMap()))
                .thenReturn("exec-123");

        executionService.executeWorkflow("user123", "wf1");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> runtimeContextCaptor = ArgumentCaptor.forClass(Map.class);
        verify(fastApiClient).execute(eq("wf1"), eq("user123"), any(), anyMap(), runtimeContextCaptor.capture());
        assertThat(runtimeContextCaptor.getValue()).isEqualTo(Map.of(
                "user_profile", Map.of(
                        "user_id", "user123",
                        "email", "user123@example.com",
                        "display_name", "김민호"
                )
        ));
    }

    @Test
    @DisplayName("워크플로우 실행 - 접근 권한 없음")
    void executeWorkflow_accessDenied() {
        when(workflowService.findWorkflowOrThrow("wf1")).thenReturn(testWorkflow);

        assertThatThrownBy(() -> executionService.executeWorkflow("other-user", "wf1"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.WORKFLOW_ACCESS_DENIED);
    }

    @Test
    @DisplayName("워크플로우 실행 - 서비스 노드의 토큰 수집")
    void executeWorkflow_collectsServiceTokens() {
        NodeDefinition serviceNode = NodeDefinition.builder()
                .id("n1").category("service").type("google").build();
        testWorkflow.setNodes(List.of(serviceNode));

        when(workflowService.findWorkflowOrThrow("wf1")).thenReturn(testWorkflow);
        when(catalogService.isAuthRequired("google")).thenReturn(true);
        when(oauthTokenService.getDecryptedToken(eq("user123"), eq("google"), anyList()))
                .thenReturn("decrypted-token");
        when(workflowTranslator.toRuntimeModel(testWorkflow)).thenReturn(Map.of());
        when(fastApiClient.execute(eq("wf1"), eq("user123"), any(), anyMap(), anyMap()))
                .thenReturn("exec-123");

        executionService.executeWorkflow("user123", "wf1");

        verify(oauthTokenService).getDecryptedToken(eq("user123"), eq("google"), anyList());
    }

    @Test
    @DisplayName("워크플로우 실행 - Gmail source/sink scope를 각각 검증한다")
    void executeWorkflow_collectsGmailTokensWithRoleScopes() {
        NodeDefinition sourceNode = NodeDefinition.builder()
                .id("gmail-source")
                .role("start")
                .category("service")
                .type("gmail")
                .build();
        NodeDefinition sinkNode = NodeDefinition.builder()
                .id("gmail-sink")
                .role("end")
                .category("service")
                .type("gmail")
                .build();
        testWorkflow.setNodes(List.of(sourceNode, sinkNode));

        when(workflowService.findWorkflowOrThrow("wf1")).thenReturn(testWorkflow);
        when(catalogService.isAuthRequired("gmail")).thenReturn(true);
        when(oauthTokenService.getDecryptedToken("user123", "gmail", List.of(GMAIL_READONLY_SCOPE)))
                .thenReturn("gmail-token");
        when(oauthTokenService.getDecryptedToken("user123", "gmail", List.of(GMAIL_SEND_SCOPE)))
                .thenReturn("gmail-token");
        when(workflowTranslator.toRuntimeModel(testWorkflow)).thenReturn(Map.of());
        when(fastApiClient.execute(eq("wf1"), eq("user123"), any(), anyMap(), anyMap()))
                .thenReturn("exec-123");

        executionService.executeWorkflow("user123", "wf1");

        verify(oauthTokenService).getDecryptedToken("user123", "gmail", List.of(GMAIL_READONLY_SCOPE));
        verify(oauthTokenService).getDecryptedToken("user123", "gmail", List.of(GMAIL_SEND_SCOPE));
    }

    @Test
    @DisplayName("실행 이력 목록 조회")
    void getExecutionsByWorkflowId() {
        when(workflowService.findWorkflowOrThrow("wf1")).thenReturn(testWorkflow);
        when(executionRepository.findByWorkflowId("wf1")).thenReturn(List.of(testExecution));

        List<ExecutionSummaryResponse> executions = executionService.getExecutionsByWorkflowId("user123", "wf1");

        assertThat(executions).hasSize(1);
    }

    @Test
    @DisplayName("실행 상세 조회 성공")
    void getExecutionDetail_success() {
        when(workflowService.findWorkflowOrThrow("wf1")).thenReturn(testWorkflow);
        when(executionRepository.findById("exec1")).thenReturn(Optional.of(testExecution));

        ExecutionDetailResponse result = executionService.getExecutionDetail("user123", "wf1", "exec1");

        assertThat(result.getId()).isEqualTo("exec1");
    }

    @Test
    @DisplayName("실행 상세 조회는 node log error code/context를 보존한다")
    void getExecutionDetail_preservesNodeLogErrorContext() {
        Map<String, Object> errorContext = Map.of(
                "filename", "archive.zip",
                "content_status", "unsupported",
                "content_error", "이 파일 형식은 아직 본문 읽기를 지원하지 않습니다."
        );
        NodeLog nodeLog = NodeLog.builder()
                .nodeId("node_1")
                .status("failed")
                .error(ErrorDetail.builder()
                        .code("DOCUMENT_CONTENT_UNSUPPORTED")
                        .message("이 파일 형식은 아직 본문 읽기를 지원하지 않습니다.")
                        .context(errorContext)
                        .build())
                .build();
        testExecution.setNodeLogs(List.of(nodeLog));

        when(workflowService.findWorkflowOrThrow("wf1")).thenReturn(testWorkflow);
        when(executionRepository.findById("exec1")).thenReturn(Optional.of(testExecution));

        ExecutionDetailResponse result = executionService.getExecutionDetail("user123", "wf1", "exec1");

        assertThat(result.getNodeLogs()).singleElement().satisfies(log -> {
            assertThat(log.getError().getCode()).isEqualTo("DOCUMENT_CONTENT_UNSUPPORTED");
            assertThat(log.getError().getContext()).isEqualTo(errorContext);
        });
    }

    @Test
    @DisplayName("실행 상세 조회 - 존재하지 않는 실행")
    void getExecutionDetail_notFound() {
        when(workflowService.findWorkflowOrThrow("wf1")).thenReturn(testWorkflow);
        when(executionRepository.findById("unknown")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> executionService.getExecutionDetail("user123", "wf1", "unknown"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.EXECUTION_NOT_FOUND);
    }

    @Test
    @DisplayName("실행 상세 조회 - 다른 사용자 접근 거부")
    void getExecutionDetail_accessDenied() {
        when(workflowService.findWorkflowOrThrow("wf1")).thenReturn(testWorkflow);

        assertThatThrownBy(() -> executionService.getExecutionDetail("other-user", "wf1", "exec1"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.WORKFLOW_ACCESS_DENIED);
    }

    @Test
    @DisplayName("롤백 요청 전달")
    void rollbackExecution() {
        when(executionRepository.findById("exec1")).thenReturn(Optional.of(testExecution));

        executionService.rollbackExecution("user123", "exec1", "node_1");

        verify(snapshotService).rollbackToSnapshot("user123", "exec1", "node_1");
    }

    @Test
    @DisplayName("실행 완료 콜백은 상태와 결과 데이터를 저장한다")
    void completeExecution_updatesResultFields() {
        Map<String, Object> output = Map.of("result", "ok");
        when(executionRepository.findById("exec1")).thenReturn(Optional.of(testExecution));
        when(mongoTemplate.updateFirst(any(Query.class), any(Update.class), eq(WorkflowExecution.class)))
                .thenReturn(UpdateResult.acknowledged(1, 1L, null));

        executionService.completeExecution("exec1", "completed", null, output, 1234L, List.of());

        ArgumentCaptor<Query> queryCaptor = ArgumentCaptor.forClass(Query.class);
        ArgumentCaptor<Update> updateCaptor = ArgumentCaptor.forClass(Update.class);
        verify(mongoTemplate).updateFirst(queryCaptor.capture(), updateCaptor.capture(), eq(WorkflowExecution.class));

        assertThat(queryCaptor.getValue().getQueryObject().get("_id")).isEqualTo("exec1");

        Document setDocument = (Document) updateCaptor.getValue().getUpdateObject().get("$set");
        assertThat(setDocument.get("state")).isEqualTo("success");
        assertThat(setDocument.get("error")).isNull();
        assertThat(setDocument.get("output")).isEqualTo(output);
        assertThat(setDocument.get("durationMs")).isEqualTo(1234L);
        assertThat(setDocument.get("finishedAt")).isInstanceOf(Instant.class);
        verify(workflowNodeStateService).applyUpdates("wf1", List.of());
    }

    @Test
    @DisplayName("노드 데이터 조회는 문서 content 상태 필드를 보존한다")
    void getNodeData_preservesDocumentContentFields() {
        Map<String, Object> contentMetadata = Map.of(
                "extraction_method", "pdf_text",
                "content_kind", "plain_text",
                "truncated", true,
                "char_count", 4000,
                "original_char_count", 82000,
                "stored_content_truncated", true,
                "stored_char_count", 1000
        );
        Map<String, Object> outputData = Map.of(
                "type", "SINGLE_FILE",
                "filename", "report.pdf",
                "content", "truncated content",
                "content_status", "available",
                "content_error", "",
                "content_metadata", contentMetadata
        );
        NodeLog nodeLog = NodeLog.builder()
                .nodeId("node_1")
                .status("success")
                .outputData(outputData)
                .build();
        testExecution.setNodeLogs(List.of(nodeLog));

        when(workflowService.findWorkflowOrThrow("wf1")).thenReturn(testWorkflow);
        when(executionRepository.findById("exec1")).thenReturn(Optional.of(testExecution));

        NodeDataResponse response = executionService.getNodeData("user123", "wf1", "exec1", "node_1");

        assertThat(response.isAvailable()).isTrue();
        assertThat(response.getOutputData())
                .containsEntry("content_status", "available")
                .containsEntry("content_error", "")
                .containsEntry("content_metadata", contentMetadata);
    }

    @Test
    @DisplayName("노드 데이터 조회는 node log error code/context를 보존한다")
    void getNodeData_preservesNodeLogErrorContext() {
        Map<String, Object> errorContext = Map.of(
                "filename", "archive.zip",
                "content_status", "unsupported",
                "content_error", "이 파일 형식은 아직 본문 읽기를 지원하지 않습니다."
        );
        NodeLog nodeLog = NodeLog.builder()
                .nodeId("node_1")
                .status("failed")
                .error(ErrorDetail.builder()
                        .code("DOCUMENT_CONTENT_UNSUPPORTED")
                        .message("이 파일 형식은 아직 본문 읽기를 지원하지 않습니다.")
                        .context(errorContext)
                        .build())
                .build();
        testExecution.setNodeLogs(List.of(nodeLog));

        when(workflowService.findWorkflowOrThrow("wf1")).thenReturn(testWorkflow);
        when(executionRepository.findById("exec1")).thenReturn(Optional.of(testExecution));

        NodeDataResponse response = executionService.getNodeData("user123", "wf1", "exec1", "node_1");

        assertThat(response.getReason()).isEqualTo("NODE_FAILED");
        assertThat(response.getError().getCode()).isEqualTo("DOCUMENT_CONTENT_UNSUPPORTED");
        assertThat(response.getError().getContext()).isEqualTo(errorContext);
    }

    @Test
    @DisplayName("실행 완료 콜백은 output의 문서 content 상태 필드를 저장 update에 포함한다")
    void completeExecution_preservesDocumentContentFieldsInOutputUpdate() {
        Map<String, Object> output = Map.of(
                "type", "SINGLE_FILE",
                "content_status", "too_large",
                "content_error", "파일이 너무 커서 본문을 읽을 수 없습니다.",
                "content_metadata", Map.of(
                        "limits", Map.of("max_download_bytes", 10485760),
                        "stored_content_truncated", false
                )
        );
        when(executionRepository.findById("exec1")).thenReturn(Optional.of(testExecution));
        when(mongoTemplate.updateFirst(any(Query.class), any(Update.class), eq(WorkflowExecution.class)))
                .thenReturn(UpdateResult.acknowledged(1, 1L, null));

        executionService.completeExecution("exec1", "completed", null, output, 1234L, List.of());

        ArgumentCaptor<Update> updateCaptor = ArgumentCaptor.forClass(Update.class);
        verify(mongoTemplate).updateFirst(any(Query.class), updateCaptor.capture(), eq(WorkflowExecution.class));

        Document setDocument = (Document) updateCaptor.getValue().getUpdateObject().get("$set");
        assertThat(setDocument.get("output")).isEqualTo(output);
    }

    @Test
    @DisplayName("실행 완료 콜백은 대상 실행이 없으면 예외를 던진다")
    void completeExecution_notFound() {
        when(executionRepository.findById("unknown")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> executionService.completeExecution("unknown", "completed", null, Map.of(), 1L, List.of()))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.EXECUTION_NOT_FOUND);
    }

    @Test
    @DisplayName("스케줄 실행은 워크플로우 실행 전 검증을 수행한다")
    void executeScheduled_validatesBeforeExecution() {
        when(workflowService.findWorkflowOrThrow("wf1")).thenReturn(testWorkflow);
        when(workflowTranslator.toRuntimeModel(testWorkflow)).thenReturn(Map.of());
        when(fastApiClient.execute(eq("wf1"), eq("user123"), any(), anyMap(), anyMap()))
                .thenReturn("exec-123");

        String executionId = executionService.executeScheduled("wf1");

        assertThat(executionId).isEqualTo("exec-123");
        verify(workflowValidator).validateForExecution(testWorkflow, nodeLifecycleService, catalogService, "user123");
    }

    @Test
    @DisplayName("웹훅 실행은 워크플로우 실행 전 검증을 수행한다")
    void executeFromWebhook_validatesBeforeExecution() {
        Map<String, Object> triggerConfig = new HashMap<>();
        Map<String, Object> trigger = new HashMap<>();
        trigger.put("config", triggerConfig);
        Map<String, Object> runtimeModel = new HashMap<>();
        runtimeModel.put("trigger", trigger);

        when(workflowService.findWorkflowOrThrow("wf1")).thenReturn(testWorkflow);
        when(workflowTranslator.toRuntimeModel(testWorkflow)).thenReturn(runtimeModel);
        when(fastApiClient.execute(eq("wf1"), eq("user123"), any(), anyMap(), anyMap()))
                .thenReturn("exec-123");

        String executionId = executionService.executeFromWebhook("wf1", Map.of("event", "created"));

        assertThat(executionId).isEqualTo("exec-123");
        assertThat(triggerConfig.get("event_payload")).isEqualTo(Map.of("event", "created"));
        verify(workflowValidator).validateForExecution(testWorkflow, nodeLifecycleService, catalogService, "user123");
    }
}
