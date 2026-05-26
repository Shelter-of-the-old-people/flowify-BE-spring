package org.github.flowify.workflow.service;

import lombok.RequiredArgsConstructor;
import org.github.flowify.catalog.service.CatalogService;
import org.github.flowify.common.dto.PageResponse;
import org.github.flowify.common.exception.BusinessException;
import org.github.flowify.common.exception.ErrorCode;
import org.github.flowify.execution.dto.ExecutionSummaryResponse;
import org.github.flowify.workflow.dto.NodeAddRequest;
import org.github.flowify.workflow.dto.NodeStatusResponse;
import org.github.flowify.workflow.dto.NodeUpdateRequest;
import org.github.flowify.workflow.dto.ValidationWarning;
import org.github.flowify.workflow.dto.WorkflowCreateRequest;
import org.github.flowify.workflow.dto.WorkflowListItemResponse;
import org.github.flowify.workflow.dto.WorkflowListProjection;
import org.github.flowify.workflow.dto.WorkflowResponse;
import org.github.flowify.workflow.dto.WorkflowUpdateRequest;
import org.github.flowify.workflow.entity.EdgeDefinition;
import org.github.flowify.workflow.entity.NodeDefinition;
import org.github.flowify.workflow.entity.TriggerConfig;
import org.github.flowify.workflow.entity.Workflow;
import org.github.flowify.workflow.repository.WorkflowRepository;
import org.github.flowify.catalog.service.NodeLifecycleService;
import org.github.flowify.workflow.service.choice.ChoiceMappingService;
import org.github.flowify.workflow.service.choice.dto.ChoiceResponse;
import org.github.flowify.workflow.service.choice.dto.NodeSelectionResult;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class WorkflowService {

    private static final String STATUS_ALL = "all";
    private static final String STATUS_RUNNING = "running";
    private static final String STATUS_STOPPED = "stopped";
    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;

    private final WorkflowRepository workflowRepository;
    private final WorkflowValidator workflowValidator;
    private final ChoiceMappingService choiceMappingService;
    private final NodeLifecycleService nodeLifecycleService;
    private final CatalogService catalogService;
    private final ApplicationEventPublisher eventPublisher;

    public WorkflowResponse createWorkflow(String userId, WorkflowCreateRequest request) {
        Workflow workflow = Workflow.builder()
                .name(request.getName())
                .description(request.getDescription())
                .userId(userId)
                .nodes(request.getNodes() != null ? request.getNodes() : new ArrayList<>())
                .edges(request.getEdges() != null ? request.getEdges() : new ArrayList<>())
                .trigger(request.getTrigger())
                .build();

        normalizeWorkflowTriggerState(workflow);
        List<ValidationWarning> warnings = workflowValidator.validate(workflow);
        validateActiveScheduleForExecution(workflow, userId);
        Workflow saved = workflowRepository.save(workflow);
        publishScheduleEvent(saved, false);
        return buildWorkflowResponse(saved, warnings, userId);
    }

    public void validateWorkflowGenerationTarget(String userId, String workflowId) {
        Workflow workflow = findWorkflowOrThrow(workflowId);
        verifyOwnership(workflow, userId);
        assertEmptyWorkflowForGeneration(workflow);
    }

    public WorkflowResponse applyGeneratedWorkflow(String userId, String workflowId, WorkflowCreateRequest request) {
        Workflow workflow = findWorkflowOrThrow(workflowId);
        verifyOwnership(workflow, userId);
        assertEmptyWorkflowForGeneration(workflow);

        boolean wasSchedule = WorkflowTriggerSupport.isSchedule(workflow.getTrigger());

        workflow.setName(request.getName());
        workflow.setDescription(request.getDescription());
        workflow.setNodes(request.getNodes() != null ? request.getNodes() : new ArrayList<>());
        workflow.setEdges(request.getEdges() != null ? request.getEdges() : new ArrayList<>());
        workflow.setTrigger(request.getTrigger());

        normalizeWorkflowTriggerState(workflow);
        List<ValidationWarning> warnings = workflowValidator.validate(workflow);
        validateActiveScheduleForExecution(workflow, userId);
        Workflow saved = workflowRepository.save(workflow);
        publishScheduleEvent(saved, wasSchedule);
        return buildWorkflowResponse(saved, warnings, userId);
    }

    public WorkflowResponse applyRefinedWorkflow(String userId, String workflowId, WorkflowCreateRequest request) {
        Workflow workflow = findWorkflowOrThrow(workflowId);
        verifyOwnership(workflow, userId);

        boolean wasSchedule = WorkflowTriggerSupport.isSchedule(workflow.getTrigger());

        workflow.setNodes(mergeRefinedNodes(workflow.getNodes(), request.getNodes()));
        workflow.setEdges(request.getEdges() != null ? request.getEdges() : new ArrayList<>());

        normalizeWorkflowTriggerState(workflow);
        List<ValidationWarning> warnings = workflowValidator.validate(workflow);
        validateActiveScheduleForExecution(workflow, userId);
        Workflow saved = workflowRepository.save(workflow);
        publishScheduleEvent(saved, wasSchedule);
        return buildWorkflowResponse(saved, warnings, userId);
    }

    public List<WorkflowResponse> getWorkflowsByUserId(String userId) {
        List<Workflow> workflows = workflowRepository
                .findByUserIdOrSharedWithContainingOrderByUpdatedAtDesc(userId, userId);

        return workflows.stream()
                .map(WorkflowResponse::from)
                .toList();
    }

    public PageResponse<WorkflowListItemResponse> getWorkflowPage(String userId, int page, int size, String status) {
        int normalizedPage = Math.max(page, 0);
        int normalizedSize = normalizePageSize(size);
        String normalizedStatus = normalizeListStatusFilter(status);

        PageRequest pageRequest = PageRequest.of(
                normalizedPage,
                normalizedSize,
                Sort.by(Sort.Direction.DESC, "updatedAt")
        );
        Page<WorkflowListProjection> workflowPage = switch (normalizedStatus) {
            case STATUS_RUNNING -> workflowRepository.findRunningListProjectionsByUserIdOrSharedWith(
                    userId,
                    userId,
                    pageRequest
            );
            case STATUS_STOPPED -> workflowRepository.findStoppedListProjectionsByUserIdOrSharedWith(
                    userId,
                    userId,
                    pageRequest
            );
            default -> workflowRepository.findListProjectionsByUserIdOrSharedWith(
                    userId,
                    userId,
                    pageRequest
            );
        };
        List<WorkflowListItemResponse> content = workflowPage.getContent().stream()
                .map(this::toListResponse)
                .toList();

        return PageResponse.of(content, normalizedPage, normalizedSize, workflowPage.getTotalElements());
    }

    public WorkflowResponse getWorkflowById(String userId, String workflowId) {
        Workflow workflow = findWorkflowOrThrow(workflowId);
        verifyAccess(workflow, userId);
        return WorkflowResponse.from(workflow);
    }

    public WorkflowResponse updateWorkflow(String userId, String workflowId, WorkflowUpdateRequest request) {
        Workflow workflow = findWorkflowOrThrow(workflowId);
        verifyAccess(workflow, userId);

        boolean wasSchedule = WorkflowTriggerSupport.isSchedule(workflow.getTrigger());
        boolean wasActiveSchedule = workflow.isActive() && wasSchedule;

        if (request.getName() != null) {
            workflow.setName(request.getName());
        }
        if (request.getDescription() != null) {
            workflow.setDescription(request.getDescription());
        }
        if (request.getNodes() != null) {
            workflow.setNodes(request.getNodes());
        }
        if (request.getEdges() != null) {
            workflow.setEdges(request.getEdges());
        }
        if (request.getTrigger() != null) {
            workflow.setTrigger(request.getTrigger());
        }
        if (request.getIsActive() != null) {
            workflow.setActive(request.getIsActive());
        }

        normalizeWorkflowTriggerState(workflow);
        List<ValidationWarning> warnings = workflowValidator.validate(workflow);
        validateActiveScheduleForExecution(workflow, userId, request, wasActiveSchedule);
        Workflow saved = workflowRepository.save(workflow);

        publishScheduleEvent(saved, wasSchedule);

        return buildWorkflowResponse(saved, warnings, userId);
    }

    public void deleteWorkflow(String userId, String workflowId) {
        Workflow workflow = findWorkflowOrThrow(workflowId);
        verifyOwnership(workflow, userId);
        boolean wasSchedule = WorkflowTriggerSupport.isSchedule(workflow.getTrigger());
        workflowRepository.delete(workflow);
        if (wasSchedule) {
            eventPublisher.publishEvent(new WorkflowScheduleEvent(workflowId, false, null, null));
        }
    }

    public void shareWorkflow(String userId, String workflowId, List<String> userIds) {
        Workflow workflow = findWorkflowOrThrow(workflowId);
        verifyOwnership(workflow, userId);
        workflow.setSharedWith(userIds);
        workflowRepository.save(workflow);
    }

    // ── 노드 단위 조작 메서드 ──

    /**
     * 이전 노드의 outputDataType을 기반으로 다음 노드 선택지를 조회한다.
     */
    public ChoiceResponse getNodeChoices(String userId, String workflowId,
                                          String prevNodeId, Map<String, Object> context) {
        Workflow workflow = findWorkflowOrThrow(workflowId);
        verifyAccess(workflow, userId);

        NodeDefinition prevNode = findNodeOrThrow(workflow, prevNodeId);
        String outputType = prevNode.getOutputDataType();

        if (outputType == null || outputType.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST,
                    "이전 노드 '" + prevNodeId + "'의 outputDataType이 설정되지 않았습니다.");
        }

        Map<String, Object> mergedContext = new HashMap<>();
        if (context != null) {
            mergedContext.putAll(context);
        }

        return choiceMappingService.getOptionsForNode(outputType, mergedContext);
    }

    /**
     * 사용자의 선택지 선택을 처리하고 노드 타입을 결정한다.
     */
    public NodeSelectionResult selectNodeChoice(String userId, String workflowId,
                                                 String prevNodeId, String selectedOptionId,
                                                 Map<String, Object> context) {
        Workflow workflow = findWorkflowOrThrow(workflowId);
        verifyAccess(workflow, userId);

        NodeDefinition prevNode = findNodeOrThrow(workflow, prevNodeId);
        String dataType = prevNode.getOutputDataType();

        if (dataType == null || dataType.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST,
                    "이전 노드 '" + prevNodeId + "'의 outputDataType이 설정되지 않았습니다.");
        }

        return choiceMappingService.onUserSelect(selectedOptionId, dataType, context);
    }

    /**
     * 확정된 노드를 워크플로우에 추가한다. prevNodeId가 지정되면 edge도 함께 생성한다.
     */
    public WorkflowResponse addMiddleNode(String userId, String workflowId, NodeAddRequest request) {
        Workflow workflow = findWorkflowOrThrow(workflowId);
        verifyOwnership(workflow, userId);

        String nodeId = "node_" + UUID.randomUUID().toString().substring(0, 8);

        NodeDefinition newNode = NodeDefinition.builder()
                .id(nodeId)
                .category(request.getCategory())
                .type(request.getType())
                .label(request.getLabel())
                .config(request.getConfig())
                .position(request.getPosition())
                .dataType(request.getDataType())
                .outputDataType(request.getOutputDataType())
                .role(request.getRole())
                .authWarning(request.isAuthWarning())
                .build();

        workflow.getNodes().add(newNode);

        if (request.getPrevNodeId() != null && !request.getPrevNodeId().isBlank()) {
            findNodeOrThrow(workflow, request.getPrevNodeId());
            String edgeId = "edge_" + UUID.randomUUID().toString().substring(0, 8);
            EdgeDefinition edge = EdgeDefinition.builder()
                    .id(edgeId)
                    .source(request.getPrevNodeId())
                    .target(nodeId)
                    .label(request.getPrevEdgeLabel())
                    .sourceHandle(request.getPrevEdgeSourceHandle())
                    .targetHandle(request.getPrevEdgeTargetHandle())
                    .build();
            workflow.getEdges().add(edge);
        }

        List<ValidationWarning> warnings = workflowValidator.validate(workflow);
        Workflow saved = workflowRepository.save(workflow);
        return buildWorkflowResponse(saved, warnings, userId);
    }

    /**
     * 기존 노드의 설정을 수정한다.
     */
    public WorkflowResponse updateNode(String userId, String workflowId,
                                        String nodeId, NodeUpdateRequest request) {
        Workflow workflow = findWorkflowOrThrow(workflowId);
        verifyOwnership(workflow, userId);

        NodeDefinition node = findNodeOrThrow(workflow, nodeId);
        int index = workflow.getNodes().indexOf(node);

        NodeDefinition updated = NodeDefinition.builder()
                .id(node.getId())
                .category(request.getCategory() != null ? request.getCategory() : node.getCategory())
                .type(request.getType() != null ? request.getType() : node.getType())
                .label(request.getLabel() != null ? request.getLabel() : node.getLabel())
                .config(request.getConfig() != null ? request.getConfig() : node.getConfig())
                .position(request.getPosition() != null ? request.getPosition() : node.getPosition())
                .dataType(request.getDataType() != null ? request.getDataType() : node.getDataType())
                .outputDataType(request.getOutputDataType() != null ? request.getOutputDataType() : node.getOutputDataType())
                .role(request.getRole() != null ? request.getRole() : node.getRole())
                .authWarning(request.getAuthWarning() != null ? request.getAuthWarning() : node.isAuthWarning())
                .build();

        workflow.getNodes().set(index, updated);
        List<ValidationWarning> warnings = workflowValidator.validate(workflow);
        Workflow saved = workflowRepository.save(workflow);
        return buildWorkflowResponse(saved, warnings, userId);
    }

    /**
     * 노드를 삭제하고, 해당 노드 이후에 연결된 후속 노드들도 캐스케이드 삭제한다.
     */
    public WorkflowResponse deleteNodeCascade(String userId, String workflowId, String nodeId) {
        Workflow workflow = findWorkflowOrThrow(workflowId);
        verifyOwnership(workflow, userId);
        findNodeOrThrow(workflow, nodeId);

        // 삭제 대상 노드 수집 (BFS로 후속 노드 탐색)
        Set<String> toDelete = new HashSet<>();
        collectDownstreamNodes(workflow, nodeId, toDelete);

        // 노드 삭제
        workflow.getNodes().removeIf(n -> toDelete.contains(n.getId()));

        // 관련 엣지 삭제
        workflow.getEdges().removeIf(e ->
                toDelete.contains(e.getSource()) || toDelete.contains(e.getTarget()));

        List<ValidationWarning> warnings = workflowValidator.validate(workflow);
        Workflow saved = workflowRepository.save(workflow);
        return buildWorkflowResponse(saved, warnings, userId);
    }

    private void collectDownstreamNodes(Workflow workflow, String startNodeId, Set<String> collected) {
        collected.add(startNodeId);
        for (EdgeDefinition edge : workflow.getEdges()) {
            if (edge.getSource().equals(startNodeId) && !collected.contains(edge.getTarget())) {
                collectDownstreamNodes(workflow, edge.getTarget(), collected);
            }
        }
    }

    private WorkflowResponse buildWorkflowResponse(Workflow workflow, List<ValidationWarning> warnings, String userId) {
        List<NodeStatusResponse> nodeStatuses = workflow.getNodes() == null || workflow.getNodes().isEmpty()
                ? null
                : nodeLifecycleService.evaluateAll(workflow.getNodes(), userId);
        return WorkflowResponse.from(workflow, warnings, nodeStatuses);
    }

    private void assertEmptyWorkflowForGeneration(Workflow workflow) {
        boolean hasNodes = workflow.getNodes() != null && !workflow.getNodes().isEmpty();
        boolean hasEdges = workflow.getEdges() != null && !workflow.getEdges().isEmpty();

        if (hasNodes || hasEdges) {
            throw new BusinessException(
                    ErrorCode.INVALID_REQUEST,
                    "AI 생성은 빈 워크플로우에서만 사용할 수 있습니다."
            );
        }
    }

    private List<NodeDefinition> mergeRefinedNodes(List<NodeDefinition> existingNodes,
                                                   List<NodeDefinition> refinedNodes) {
        if (refinedNodes == null) {
            return new ArrayList<>();
        }

        Map<String, NodeDefinition> existingById = new HashMap<>();
        if (existingNodes != null) {
            for (NodeDefinition node : existingNodes) {
                if (node.getId() != null) {
                    existingById.put(node.getId(), node);
                }
            }
        }

        List<NodeDefinition> mergedNodes = new ArrayList<>();
        for (NodeDefinition refinedNode : refinedNodes) {
            NodeDefinition existingNode = existingById.get(refinedNode.getId());
            mergedNodes.add(mergeRefinedNode(existingNode, refinedNode));
        }
        return mergedNodes;
    }

    private NodeDefinition mergeRefinedNode(NodeDefinition existingNode, NodeDefinition refinedNode) {
        Map<String, Object> config = refinedNode.getConfig();
        if (isSameNodeIdentity(existingNode, refinedNode)) {
            config = mergeRefinedConfig(existingNode.getConfig(), refinedNode.getConfig());
        }

        return NodeDefinition.builder()
                .id(refinedNode.getId())
                .category(refinedNode.getCategory())
                .type(refinedNode.getType())
                .label(refinedNode.getLabel())
                .config(config)
                .position(refinedNode.getPosition())
                .dataType(refinedNode.getDataType())
                .outputDataType(refinedNode.getOutputDataType())
                .role(refinedNode.getRole())
                .authWarning(refinedNode.isAuthWarning())
                .build();
    }

    private boolean isSameNodeIdentity(NodeDefinition existingNode, NodeDefinition refinedNode) {
        return existingNode != null
                && Objects.equals(existingNode.getId(), refinedNode.getId())
                && Objects.equals(existingNode.getType(), refinedNode.getType())
                && Objects.equals(existingNode.getRole(), refinedNode.getRole());
    }

    private Map<String, Object> mergeRefinedConfig(Map<String, Object> existingConfig,
                                                   Map<String, Object> refinedConfig) {
        Map<String, Object> mergedConfig = new LinkedHashMap<>();
        if (existingConfig != null) {
            for (Map.Entry<String, Object> entry : existingConfig.entrySet()) {
                if (!"isConfigured".equals(entry.getKey())) {
                    mergedConfig.put(entry.getKey(), entry.getValue());
                }
            }
        }

        if (refinedConfig != null) {
            for (Map.Entry<String, Object> entry : refinedConfig.entrySet()) {
                if ("isConfigured".equals(entry.getKey())) {
                    continue;
                }
                Object existingValue = mergedConfig.get(entry.getKey());
                Object refinedValue = entry.getValue();
                if (isMissingConfigValue(refinedValue) && !isMissingConfigValue(existingValue)) {
                    continue;
                }
                mergedConfig.put(entry.getKey(), refinedValue);
            }
        }
        return mergedConfig;
    }

    private boolean isMissingConfigValue(Object value) {
        if (value == null) {
            return true;
        }
        if (value instanceof String text) {
            return text.isBlank();
        }
        if (value instanceof List<?> list) {
            return list.isEmpty();
        }
        if (value instanceof Map<?, ?> map) {
            return map.isEmpty();
        }
        return false;
    }

    private NodeDefinition findNodeOrThrow(Workflow workflow, String nodeId) {
        return workflow.getNodes().stream()
                .filter(n -> n.getId().equals(nodeId))
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_REQUEST,
                        "노드 '" + nodeId + "'을(를) 찾을 수 없습니다."));
    }

    public Workflow findWorkflowOrThrow(String workflowId) {
        return workflowRepository.findById(workflowId)
                .orElseThrow(() -> new BusinessException(ErrorCode.WORKFLOW_NOT_FOUND));
    }

    private void verifyOwnership(Workflow workflow, String userId) {
        if (!workflow.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.WORKFLOW_ACCESS_DENIED);
        }
    }

    private void verifyAccess(Workflow workflow, String userId) {
        if (!workflow.getUserId().equals(userId)
                && !workflow.getSharedWith().contains(userId)) {
            throw new BusinessException(ErrorCode.WORKFLOW_ACCESS_DENIED);
        }
    }

    private void publishScheduleEvent(Workflow saved, boolean wasSchedule) {
        TriggerConfig trigger = saved.getTrigger();
        boolean isSchedule = WorkflowTriggerSupport.isSchedule(trigger);

        if (isSchedule) {
            String cron = WorkflowTriggerSupport.getCron(trigger);
            String timezone = WorkflowTriggerSupport.getTimezone(trigger);
            boolean shouldRegister = saved.isActive() && !cron.isBlank();
            eventPublisher.publishEvent(new WorkflowScheduleEvent(saved.getId(), shouldRegister, cron, timezone));
        } else if (wasSchedule) {
            eventPublisher.publishEvent(new WorkflowScheduleEvent(saved.getId(), false, null, null));
        }
    }

    private void validateActiveScheduleForExecution(Workflow workflow, String userId) {
        if (!workflow.isActive() || !WorkflowTriggerSupport.isSchedule(workflow.getTrigger())) {
            return;
        }

        workflowValidator.validateForExecution(workflow, nodeLifecycleService, catalogService, userId);
    }

    private void validateActiveScheduleForExecution(Workflow workflow, String userId,
                                                    WorkflowUpdateRequest request, boolean wasActiveSchedule) {
        if (!shouldValidateActiveSchedule(workflow, request, wasActiveSchedule)) {
            return;
        }

        workflowValidator.validateForExecution(workflow, nodeLifecycleService, catalogService, userId);
    }

    private boolean shouldValidateActiveSchedule(Workflow workflow, WorkflowUpdateRequest request,
                                                 boolean wasActiveSchedule) {
        if (!workflow.isActive() || !WorkflowTriggerSupport.isSchedule(workflow.getTrigger())) {
            return false;
        }
        if (!wasActiveSchedule) {
            return true;
        }
        return Boolean.TRUE.equals(request.getIsActive())
                || request.getTrigger() != null
                || request.getNodes() != null
                || request.getEdges() != null;
    }

    private void normalizeWorkflowTriggerState(Workflow workflow) {
        TriggerConfig normalizedTrigger = WorkflowTriggerSupport.normalizeTrigger(workflow.getTrigger());
        workflow.setTrigger(normalizedTrigger);
        workflow.setActive(WorkflowTriggerSupport.normalizeActive(normalizedTrigger, workflow.isActive()));
    }

    private int normalizePageSize(int size) {
        if (size <= 0) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(size, MAX_PAGE_SIZE);
    }

    private String normalizeListStatusFilter(String status) {
        if (status == null) {
            return STATUS_ALL;
        }

        String normalizedStatus = status.trim().toLowerCase(Locale.ROOT);
        if (STATUS_RUNNING.equals(normalizedStatus) || STATUS_STOPPED.equals(normalizedStatus)) {
            return normalizedStatus;
        }
        return STATUS_ALL;
    }

    private WorkflowListItemResponse toListResponse(Workflow workflow) {
        ExecutionSummaryResponse latestExecutionSummary = toExecutionSummary(workflow);
        return WorkflowListItemResponse.from(
                workflow,
                latestExecutionSummary,
                resolveListStatus(workflow),
                workflowValidator.collectListWarnings(workflow)
        );
    }

    private WorkflowListItemResponse toListResponse(WorkflowListProjection workflow) {
        ExecutionSummaryResponse latestExecutionSummary = toExecutionSummary(workflow);
        return WorkflowListItemResponse.from(
                workflow,
                latestExecutionSummary,
                resolveListStatus(workflow),
                workflowValidator.collectListWarnings(workflow.getNodes(), workflow.getEdges())
        );
    }

    private ExecutionSummaryResponse toExecutionSummary(Workflow workflow) {
        return ExecutionSummaryResponse.fromWorkflowSnapshot(
                workflow.getId(),
                workflow.getLatestExecutionId(),
                workflow.getLatestExecutionState(),
                workflow.getLatestExecutionStartedAt(),
                workflow.getLatestExecutionFinishedAt(),
                workflow.getNodes() != null ? workflow.getNodes().size() : 0
        );
    }

    private ExecutionSummaryResponse toExecutionSummary(WorkflowListProjection workflow) {
        return ExecutionSummaryResponse.fromWorkflowSnapshot(
                workflow.getId(),
                workflow.getLatestExecutionId(),
                workflow.getLatestExecutionState(),
                workflow.getLatestExecutionStartedAt(),
                workflow.getLatestExecutionFinishedAt(),
                workflow.getNodes() != null ? workflow.getNodes().size() : 0
        );
    }

    private String resolveListStatus(Workflow workflow) {
        if (isInFlight(workflow.getLatestExecutionState())) {
            return STATUS_RUNNING;
        }
        if (WorkflowTriggerSupport.isSchedule(workflow.getTrigger()) && workflow.isActive()) {
            return STATUS_RUNNING;
        }
        return STATUS_STOPPED;
    }

    private String resolveListStatus(WorkflowListProjection workflow) {
        if (isInFlight(workflow.getLatestExecutionState())) {
            return STATUS_RUNNING;
        }
        if (WorkflowTriggerSupport.isSchedule(workflow.getTrigger()) && workflow.isActive()) {
            return STATUS_RUNNING;
        }
        return STATUS_STOPPED;
    }

    private boolean isInFlight(String state) {
        return "pending".equals(state) || STATUS_RUNNING.equals(state);
    }
}
