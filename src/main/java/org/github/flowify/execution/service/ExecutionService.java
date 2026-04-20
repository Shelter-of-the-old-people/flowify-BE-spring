package org.github.flowify.execution.service;

import lombok.RequiredArgsConstructor;
import org.github.flowify.common.exception.BusinessException;
import org.github.flowify.common.exception.ErrorCode;
import org.github.flowify.execution.entity.WorkflowExecution;
import org.github.flowify.execution.repository.ExecutionRepository;
import org.github.flowify.oauth.service.OAuthTokenService;
import org.github.flowify.workflow.entity.NodeDefinition;
import org.github.flowify.workflow.entity.Workflow;
import org.github.flowify.workflow.service.WorkflowService;
import org.github.flowify.workflow.service.WorkflowValidator;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ExecutionService {

    private final ExecutionRepository executionRepository;
    private final WorkflowService workflowService;
    private final FastApiClient fastApiClient;
    private final OAuthTokenService oauthTokenService;
    private final SnapshotService snapshotService;
    private final WorkflowValidator workflowValidator;
    private final WorkflowTranslator workflowTranslator;

    public String executeWorkflow(String userId, String workflowId) {
        Workflow workflow = workflowService.findWorkflowOrThrow(workflowId);

        if (!workflow.getUserId().equals(userId)
                && !workflow.getSharedWith().contains(userId)) {
            throw new BusinessException(ErrorCode.WORKFLOW_ACCESS_DENIED);
        }

        workflowValidator.validate(workflow);

        Map<String, String> serviceTokens = collectServiceTokens(userId, workflow.getNodes());

        Map<String, Object> runtimeModel = workflowTranslator.toRuntimeModel(workflow);
        return fastApiClient.execute(workflowId, userId, runtimeModel, serviceTokens);
    }

    public List<WorkflowExecution> getExecutionsByWorkflowId(String userId, String workflowId) {
        Workflow workflow = workflowService.findWorkflowOrThrow(workflowId);

        if (!workflow.getUserId().equals(userId)
                && !workflow.getSharedWith().contains(userId)) {
            throw new BusinessException(ErrorCode.WORKFLOW_ACCESS_DENIED);
        }

        return executionRepository.findByWorkflowId(workflowId);
    }

    public WorkflowExecution getExecutionDetail(String userId, String executionId) {
        WorkflowExecution execution = executionRepository.findById(executionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.EXECUTION_NOT_FOUND));

        if (!execution.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.WORKFLOW_ACCESS_DENIED);
        }

        return execution;
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

    private Map<String, String> collectServiceTokens(String userId, List<NodeDefinition> nodes) {
        Map<String, String> tokens = new HashMap<>();

        nodes.stream()
                .filter(node -> "service".equals(node.getCategory()))
                .map(NodeDefinition::getType)
                .distinct()
                .forEach(service -> {
                    try {
                        String token = oauthTokenService.getDecryptedToken(userId, service);
                        tokens.put(service, token);
                    } catch (BusinessException e) {
                        // 서비스 미연결 시 에러 전파
                        throw new BusinessException(ErrorCode.OAUTH_NOT_CONNECTED,
                                service + " 서비스가 연결되지 않았습니다.");
                    }
                });

        return tokens;
    }
}
