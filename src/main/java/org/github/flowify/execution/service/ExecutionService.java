package org.github.flowify.execution.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.github.flowify.catalog.service.CatalogService;
import org.github.flowify.catalog.service.NodeLifecycleService;
import org.github.flowify.common.exception.BusinessException;
import org.github.flowify.common.exception.ErrorCode;
import org.github.flowify.execution.dto.ExecutionDetailResponse;
import org.github.flowify.execution.dto.ExecutionSummaryResponse;
import org.github.flowify.execution.dto.NodeDataResponse;
import org.github.flowify.execution.dto.NodeStateUpdateRequest;
import org.github.flowify.execution.entity.NodeLog;
import org.github.flowify.execution.entity.WorkflowExecution;
import org.github.flowify.execution.repository.ExecutionRepository;
import org.github.flowify.oauth.service.OAuthTokenService;
import org.github.flowify.workflow.entity.NodeDefinition;
import org.github.flowify.workflow.entity.Workflow;
import org.github.flowify.workflow.service.WorkflowService;
import org.github.flowify.workflow.service.WorkflowTriggerSupport;
import org.github.flowify.workflow.service.WorkflowValidator;
import org.github.flowify.workflow.state.service.WorkflowNodeStateService;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class ExecutionService {

    private static final String GMAIL_READONLY_SCOPE = "https://www.googleapis.com/auth/gmail.readonly";
    private static final String GMAIL_SEND_SCOPE = "https://www.googleapis.com/auth/gmail.send";
    private static final String GOOGLE_DRIVE_METADATA_SCOPE = "https://www.googleapis.com/auth/drive.metadata";

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
    private final WorkflowNodeStateService workflowNodeStateService;
    private final RuntimeContextService runtimeContextService;

    public String executeWorkflow(String userId, String workflowId) {
        Workflow workflow = workflowService.findWorkflowOrThrow(workflowId);

        if (!workflow.getUserId().equals(userId)
                && !workflow.getSharedWith().contains(userId)) {
            throw new BusinessException(ErrorCode.WORKFLOW_ACCESS_DENIED);
        }

        workflowValidator.validateForExecution(workflow, nodeLifecycleService, catalogService, userId);

        Map<String, String> serviceTokens = collectServiceTokens(userId, workflow.getNodes());
        Map<String, Object> runtimeModel = workflowTranslator.toRuntimeModel(workflow);
        String executionId = fastApiClient.execute(
                workflowId,
                userId,
                runtimeModel,
                serviceTokens,
                runtimeContextFor(userId)
        );

        createExecutionRecord(executionId, workflowId, userId);
        return executionId;
    }

    public ExecutionSummaryResponse getLatestExecution(String userId, String workflowId) {
        Workflow workflow = workflowService.findWorkflowOrThrow(workflowId);

        if (!workflow.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.WORKFLOW_ACCESS_DENIED);
        }

        return executionRepository.findFirstByWorkflowIdOrderByStartedAtDesc(workflowId)
                .map(ExecutionSummaryResponse::from)
                .orElse(null);
    }

    public List<ExecutionSummaryResponse> getExecutionsByWorkflowId(String userId, String workflowId) {
        Workflow workflow = workflowService.findWorkflowOrThrow(workflowId);

        if (!workflow.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.WORKFLOW_ACCESS_DENIED);
        }

        return executionRepository.findByWorkflowId(workflowId).stream()
                .map(ExecutionSummaryResponse::from)
                .toList();
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

        if (WorkflowTriggerSupport.isSkipIfRunning(workflow.getTrigger())
                && hasInFlightExecution(workflowId)) {
            log.info("Skipping scheduled execution for workflow {} because another execution is in flight", workflowId);
            return null;
        }

        workflowValidator.validateForExecution(workflow, nodeLifecycleService, catalogService, userId);

        Map<String, String> tokens = collectServiceTokens(userId, workflow.getNodes());
        Map<String, Object> runtimeModel = workflowTranslator.toRuntimeModel(workflow);
        String executionId = fastApiClient.execute(
                workflowId,
                userId,
                runtimeModel,
                tokens,
                runtimeContextFor(userId)
        );

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
            triggerSection.computeIfAbsent("config", ignored -> new HashMap<>());
            ((Map<String, Object>) triggerSection.get("config")).put("event_payload", eventPayload);
        }

        String executionId = fastApiClient.execute(
                workflowId,
                userId,
                runtimeModel,
                tokens,
                runtimeContextFor(userId)
        );

        createExecutionRecord(executionId, workflowId, userId);
        return executionId;
    }

    public void completeExecution(
            String execId,
            String status,
            String error,
            Map<String, Object> output,
            Long durationMs,
            List<NodeStateUpdateRequest> nodeStateUpdates
    ) {
        String normalizedState = "completed".equals(status) ? "success" : status;
        WorkflowExecution execution = executionRepository.findById(execId)
                .orElseThrow(() -> new BusinessException(ErrorCode.EXECUTION_NOT_FOUND));

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

        if ("success".equals(normalizedState)) {
            workflowNodeStateService.applyUpdates(execution.getWorkflowId(), nodeStateUpdates);
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

    private boolean hasInFlightExecution(String workflowId) {
        return executionRepository.findFirstByWorkflowIdOrderByStartedAtDesc(workflowId)
                .map(execution -> "pending".equals(execution.getState()) || "running".equals(execution.getState()))
                .orElse(false);
    }

    private Map<String, String> collectServiceTokens(String userId, List<NodeDefinition> nodes) {
        Map<String, String> tokens = new HashMap<>();

        nodes.stream()
                .filter(node -> node.getType() != null)
                .filter(node -> catalogService.isAuthRequired(node.getType()))
                .forEach(node -> {
                    String service = node.getType();
                    try {
                        String token = oauthTokenService.getDecryptedToken(
                                userId, service, requiredScopes(node));
                        tokens.put(service, token);
                    } catch (BusinessException e) {
                        if (e.getErrorCode() == ErrorCode.OAUTH_SCOPE_INSUFFICIENT) {
                            throw e;
                        }
                        throw new BusinessException(ErrorCode.OAUTH_NOT_CONNECTED,
                                service + " 서비스가 연결되지 않았습니다.");
                    }
                });

        return tokens;
    }

    private List<String> requiredScopes(NodeDefinition node) {
        if ("google_drive".equals(node.getType()) && isGoogleDriveMoveSink(node)) {
            return List.of(GOOGLE_DRIVE_METADATA_SCOPE);
        }
        if ("gmail".equals(node.getType()) && "start".equals(node.getRole())) {
            return List.of(GMAIL_READONLY_SCOPE);
        }
        if ("gmail".equals(node.getType()) && "end".equals(node.getRole())) {
            return List.of(GMAIL_SEND_SCOPE);
        }
        return List.of();
    }

    private boolean isGoogleDriveMoveSink(NodeDefinition node) {
        if (!"end".equals(node.getRole()) || node.getConfig() == null) {
            return false;
        }
        return "move".equals(String.valueOf(node.getConfig().get("drive_action")));
    }

    private Map<String, Object> runtimeContextFor(String userId) {
        Map<String, Object> runtimeContext = runtimeContextService.buildForUser(userId);
        return runtimeContext != null ? runtimeContext : Map.of();
    }
}
