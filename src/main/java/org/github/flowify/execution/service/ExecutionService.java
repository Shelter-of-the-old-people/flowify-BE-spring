package org.github.flowify.execution.service;

import lombok.RequiredArgsConstructor;
import org.github.flowify.catalog.service.CatalogService;
import org.github.flowify.catalog.service.NodeLifecycleService;
import org.github.flowify.common.exception.BusinessException;
import org.github.flowify.common.exception.ErrorCode;
import org.github.flowify.execution.dto.ExecutionDetailResponse;
import org.github.flowify.execution.dto.ExecutionSummaryResponse;
import org.github.flowify.execution.dto.NodeDataResponse;
import org.github.flowify.execution.entity.NodeLog;
import org.github.flowify.execution.entity.WorkflowExecution;
import org.github.flowify.execution.repository.ExecutionRepository;
import org.github.flowify.oauth.service.OAuthTokenService;
import org.github.flowify.workflow.entity.NodeDefinition;
import org.github.flowify.workflow.entity.Workflow;
import org.github.flowify.workflow.service.WorkflowService;
import org.github.flowify.workflow.service.WorkflowValidator;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class ExecutionService {

    private final ExecutionRepository executionRepository;
    private final WorkflowService workflowService;
    private final MongoTemplate mongoTemplate;
    private final FastApiClient fastApiClient;
    private final OAuthTokenService oauthTokenService;
    private final CatalogService catalogService;
    private final NodeLifecycleService nodeLifecycleService;
    private final SnapshotService snapshotService;
    private final WorkflowValidator workflowValidator;
    private final WorkflowTranslator workflowTranslator;

    public String executeWorkflow(String userId, String workflowId) {
        Workflow workflow = workflowService.findWorkflowOrThrow(workflowId);

        if (!workflow.getUserId().equals(userId)
                && !workflow.getSharedWith().contains(userId)) {
            throw new BusinessException(ErrorCode.WORKFLOW_ACCESS_DENIED);
        }

        workflowValidator.validateForExecution(workflow, nodeLifecycleService, catalogService, userId);

        Map<String, String> serviceTokens = collectServiceTokens(userId, workflow.getNodes());

        Map<String, Object> runtimeModel = workflowTranslator.toRuntimeModel(workflow);
        String executionId = fastApiClient.execute(workflowId, userId, runtimeModel, serviceTokens);

        createExecutionRecord(executionId, workflowId, userId);
        return executionId;
    }

    public ExecutionSummaryResponse getLatestExecution(String userId, String workflowId) {
        Workflow workflow = workflowService.findWorkflowOrThrow(workflowId);

        if (!workflow.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.WORKFLOW_ACCESS_DENIED);
        }

        return executionRepository.findFirstByWorkflowIdOrderByStartedAtDesc(workflowId)
                .map(this::toSummary)
                .orElse(null);
    }

    public List<ExecutionSummaryResponse> getExecutionsByWorkflowId(String userId, String workflowId) {
        Workflow workflow = workflowService.findWorkflowOrThrow(workflowId);

        if (!workflow.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.WORKFLOW_ACCESS_DENIED);
        }

        return executionRepository.findByWorkflowId(workflowId).stream()
                .map(this::toSummary)
                .toList();
    }

    private ExecutionSummaryResponse toSummary(WorkflowExecution exec) {
        List<NodeLog> logs = exec.getNodeLogs();
        int nodeCount = logs != null ? logs.size() : 0;
        int completedNodeCount = logs != null
                ? (int) logs.stream().filter(l -> "success".equals(l.getStatus())).count()
                : 0;

        return ExecutionSummaryResponse.builder()
                .id(exec.getId())
                .workflowId(exec.getWorkflowId())
                .state(exec.getState())
                .startedAt(exec.getStartedAt())
                .finishedAt(exec.getFinishedAt())
                .durationMs(exec.getDurationMs())
                .errorMessage(exec.getError())
                .nodeCount(nodeCount)
                .completedNodeCount(completedNodeCount)
                .build();
    }

    public ExecutionDetailResponse getExecutionDetail(String userId, String workflowId, String executionId) {
        Workflow workflow = workflowService.findWorkflowOrThrow(workflowId);

        if (!workflow.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.WORKFLOW_ACCESS_DENIED);
        }

        WorkflowExecution execution = executionRepository.findById(executionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.EXECUTION_NOT_FOUND));

        if (!workflowId.equals(execution.getWorkflowId())) {
            throw new BusinessException(ErrorCode.EXECUTION_NOT_FOUND);
        }

        return ExecutionDetailResponse.builder()
                .id(execution.getId())
                .workflowId(execution.getWorkflowId())
                .state(execution.getState())
                .startedAt(execution.getStartedAt())
                .finishedAt(execution.getFinishedAt())
                .durationMs(execution.getDurationMs())
                .errorMessage(execution.getError())
                .nodeLogs(execution.getNodeLogs())
                .build();
    }

    public NodeDataResponse getNodeData(String userId, String workflowId, String executionId, String nodeId) {
        Workflow workflow = workflowService.findWorkflowOrThrow(workflowId);

        if (!workflow.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.WORKFLOW_ACCESS_DENIED);
        }

        WorkflowExecution execution = executionRepository.findById(executionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.EXECUTION_NOT_FOUND));

        if (!workflowId.equals(execution.getWorkflowId())) {
            throw new BusinessException(ErrorCode.EXECUTION_NOT_FOUND);
        }

        return buildNodeDataResponse(execution, workflowId, nodeId);
    }

    public NodeDataResponse getLatestNodeData(String userId, String workflowId, String nodeId) {
        Workflow workflow = workflowService.findWorkflowOrThrow(workflowId);

        if (!workflow.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.WORKFLOW_ACCESS_DENIED);
        }

        return executionRepository.findFirstByWorkflowIdOrderByStartedAtDesc(workflowId)
                .map(exec -> buildNodeDataResponse(exec, workflowId, nodeId))
                .orElse(NodeDataResponse.builder()
                        .workflowId(workflowId)
                        .nodeId(nodeId)
                        .available(false)
                        .reason("NO_EXECUTION")
                        .build());
    }

    private NodeDataResponse buildNodeDataResponse(WorkflowExecution execution, String workflowId, String nodeId) {
        if ("running".equals(execution.getState())) {
            return NodeDataResponse.builder()
                    .executionId(execution.getId())
                    .workflowId(workflowId)
                    .nodeId(nodeId)
                    .available(false)
                    .reason("EXECUTION_RUNNING")
                    .build();
        }

        if (execution.getNodeLogs() != null) {
            for (NodeLog log : execution.getNodeLogs()) {
                if (nodeId.equals(log.getNodeId())) {
                    boolean hasData = log.getInputData() != null || log.getOutputData() != null;
                    String reason = null;

                    if ("skipped".equals(log.getStatus())) {
                        reason = "NODE_SKIPPED";
                    } else if ("failed".equals(log.getStatus())) {
                        reason = "NODE_FAILED";
                    } else if (!hasData) {
                        reason = "DATA_EMPTY";
                    }

                    return NodeDataResponse.builder()
                            .executionId(execution.getId())
                            .workflowId(workflowId)
                            .nodeId(nodeId)
                            .status(log.getStatus())
                            .inputData(log.getInputData())
                            .outputData(log.getOutputData())
                            .snapshot(log.getSnapshot())
                            .error(log.getError())
                            .startedAt(log.getStartedAt())
                            .finishedAt(log.getFinishedAt())
                            .available(hasData)
                            .reason(reason)
                            .build();
                }
            }
        }

        return NodeDataResponse.builder()
                .executionId(execution.getId())
                .workflowId(workflowId)
                .nodeId(nodeId)
                .available(false)
                .reason("NODE_NOT_EXECUTED")
                .build();
    }

    public void stopExecution(String userId, String executionId) {
        WorkflowExecution execution = executionRepository.findById(executionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.EXECUTION_NOT_FOUND));

        if (!execution.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.WORKFLOW_ACCESS_DENIED);
        }

        if (!"running".equals(execution.getState())) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "실행 중인 워크플로우만 중지할 수 있습니다.");
        }

        fastApiClient.stopExecution(executionId, userId);
    }

    public void rollbackExecution(String userId, String executionId, String nodeId) {
        WorkflowExecution execution = executionRepository.findById(executionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.EXECUTION_NOT_FOUND));

        if (!execution.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.WORKFLOW_ACCESS_DENIED);
        }

        snapshotService.rollbackToSnapshot(userId, executionId, nodeId);
    }

    public String executeScheduled(String workflowId) {
        Workflow workflow = workflowService.findWorkflowOrThrow(workflowId);
        String userId = workflow.getUserId();

        workflowValidator.validateForExecution(workflow, nodeLifecycleService, catalogService, userId);

        Map<String, String> tokens = collectServiceTokens(userId, workflow.getNodes());
        Map<String, Object> runtimeModel = workflowTranslator.toRuntimeModel(workflow);
        String executionId = fastApiClient.execute(workflowId, userId, runtimeModel, tokens);

        createExecutionRecord(executionId, workflowId, userId);
        return executionId;
    }

    @SuppressWarnings("unchecked")
    public String executeFromWebhook(String workflowId, Map<String, Object> eventPayload) {
        Workflow workflow = workflowService.findWorkflowOrThrow(workflowId);
        String userId = workflow.getUserId();

        workflowValidator.validateForExecution(workflow, nodeLifecycleService, catalogService, userId);

        Map<String, String> tokens = collectServiceTokens(userId, workflow.getNodes());
        Map<String, Object> runtimeModel = workflowTranslator.toRuntimeModel(workflow);

        Map<String, Object> triggerSection = (Map<String, Object>) runtimeModel.get("trigger");
        if (triggerSection != null) {
            triggerSection.computeIfAbsent("config", k -> new HashMap<>());
            ((Map<String, Object>) triggerSection.get("config")).put("event_payload", eventPayload);
        }

        String executionId = fastApiClient.execute(workflowId, userId, runtimeModel, tokens);

        createExecutionRecord(executionId, workflowId, userId);
        return executionId;
    }

    public void completeExecution(String execId, String status, String error,
                                   Map<String, Object> output, Long durationMs) {
        // 상태 정규화: FastAPI가 "completed"를 보내면 "success"로 저장
        String normalizedState = "completed".equals(status) ? "success" : status;

        Query query = Query.query(Criteria.where("_id").is(execId));
        Update update = new Update()
                .set("state", normalizedState)
                .set("finishedAt", Instant.now())
                .set("error", error)
                .set("output", output)
                .set("durationMs", durationMs);

        long matched = mongoTemplate.updateFirst(query, update, WorkflowExecution.class).getMatchedCount();
        if (matched == 0) {
            throw new BusinessException(ErrorCode.EXECUTION_NOT_FOUND);
        }
    }

    private void createExecutionRecord(String executionId, String workflowId, String userId) {
        WorkflowExecution execution = WorkflowExecution.builder()
                .id(executionId)
                .workflowId(workflowId)
                .userId(userId)
                .state("running")
                .startedAt(Instant.now())
                .build();
        executionRepository.save(execution);
    }

    private Map<String, String> collectServiceTokens(String userId, List<NodeDefinition> nodes) {
        Map<String, String> tokens = new HashMap<>();

        nodes.stream()
                .map(NodeDefinition::getType)
                .filter(Objects::nonNull)
                .distinct()
                .filter(catalogService::isAuthRequired)
                .forEach(service -> {
                    try {
                        String token = oauthTokenService.getDecryptedToken(userId, service);
                        tokens.put(service, token);
                    } catch (BusinessException e) {
                        throw new BusinessException(ErrorCode.OAUTH_NOT_CONNECTED,
                                service + " 서비스가 연결되지 않았습니다.");
                    }
                });

        return tokens;
    }
}
