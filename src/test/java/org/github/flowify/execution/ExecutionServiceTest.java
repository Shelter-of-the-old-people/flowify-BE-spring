package org.github.flowify.execution;

import com.mongodb.client.result.UpdateResult;
import org.github.flowify.common.exception.BusinessException;
import org.github.flowify.common.exception.ErrorCode;
import org.github.flowify.catalog.service.CatalogService;
import org.github.flowify.catalog.service.NodeLifecycleService;
import org.github.flowify.execution.dto.ExecutionDetailResponse;
import org.github.flowify.execution.dto.ExecutionSummaryResponse;
import org.github.flowify.execution.entity.WorkflowExecution;
import org.github.flowify.execution.repository.ExecutionRepository;
import org.github.flowify.execution.service.ExecutionService;
import org.github.flowify.execution.service.FastApiClient;
import org.github.flowify.execution.service.SnapshotService;
import org.github.flowify.execution.service.WorkflowTranslator;
import org.github.flowify.oauth.service.OAuthTokenService;
import org.github.flowify.workflow.entity.NodeDefinition;
import org.github.flowify.workflow.entity.Workflow;
import org.github.flowify.workflow.service.WorkflowService;
import org.github.flowify.workflow.service.WorkflowValidator;
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
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExecutionServiceTest {

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
        when(fastApiClient.execute(eq("wf1"), eq("user123"), any(), anyMap()))
                .thenReturn("exec-123");

        String executionId = executionService.executeWorkflow("user123", "wf1");

        assertThat(executionId).isEqualTo("exec-123");
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
        when(oauthTokenService.getDecryptedToken("user123", "google")).thenReturn("decrypted-token");
        when(workflowTranslator.toRuntimeModel(testWorkflow)).thenReturn(Map.of());
        when(fastApiClient.execute(eq("wf1"), eq("user123"), any(), anyMap()))
                .thenReturn("exec-123");

        executionService.executeWorkflow("user123", "wf1");

        verify(oauthTokenService).getDecryptedToken("user123", "google");
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
        when(mongoTemplate.updateFirst(any(Query.class), any(Update.class), eq(WorkflowExecution.class)))
                .thenReturn(UpdateResult.acknowledged(1, 1L, null));

        executionService.completeExecution("exec1", "completed", null, output, 1234L);

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
    }

    @Test
    @DisplayName("실행 완료 콜백은 대상 실행이 없으면 예외를 던진다")
    void completeExecution_notFound() {
        when(mongoTemplate.updateFirst(any(Query.class), any(Update.class), eq(WorkflowExecution.class)))
                .thenReturn(UpdateResult.acknowledged(0, 0L, null));

        assertThatThrownBy(() -> executionService.completeExecution("unknown", "completed", null, Map.of(), 1L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.EXECUTION_NOT_FOUND);
    }

    @Test
    @DisplayName("스케줄 실행은 워크플로우 실행 전 검증을 수행한다")
    void executeScheduled_validatesBeforeExecution() {
        when(workflowService.findWorkflowOrThrow("wf1")).thenReturn(testWorkflow);
        when(workflowTranslator.toRuntimeModel(testWorkflow)).thenReturn(Map.of());
        when(fastApiClient.execute(eq("wf1"), eq("user123"), any(), anyMap()))
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
        when(fastApiClient.execute(eq("wf1"), eq("user123"), any(), anyMap()))
                .thenReturn("exec-123");

        String executionId = executionService.executeFromWebhook("wf1", Map.of("event", "created"));

        assertThat(executionId).isEqualTo("exec-123");
        assertThat(triggerConfig.get("event_payload")).isEqualTo(Map.of("event", "created"));
        verify(workflowValidator).validateForExecution(testWorkflow, nodeLifecycleService, catalogService, "user123");
    }
}
