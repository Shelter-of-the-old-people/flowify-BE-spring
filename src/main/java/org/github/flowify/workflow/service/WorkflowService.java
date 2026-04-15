package org.github.flowify.workflow.service;

import lombok.RequiredArgsConstructor;
import org.github.flowify.common.dto.PageResponse;
import org.github.flowify.common.exception.BusinessException;
import org.github.flowify.common.exception.ErrorCode;
import org.github.flowify.workflow.dto.NodeAddRequest;
import org.github.flowify.workflow.dto.NodeUpdateRequest;
import org.github.flowify.workflow.dto.ValidationWarning;
import org.github.flowify.workflow.dto.WorkflowCreateRequest;
import org.github.flowify.workflow.dto.WorkflowResponse;
import org.github.flowify.workflow.dto.WorkflowUpdateRequest;
import org.github.flowify.workflow.entity.EdgeDefinition;
import org.github.flowify.workflow.entity.NodeDefinition;
import org.github.flowify.workflow.entity.Workflow;
import org.github.flowify.workflow.repository.WorkflowRepository;
import org.github.flowify.workflow.service.choice.ChoiceMappingService;
import org.github.flowify.workflow.service.choice.dto.ChoiceResponse;
import org.github.flowify.workflow.service.choice.dto.NodeSelectionResult;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class WorkflowService {

    private final WorkflowRepository workflowRepository;
    private final WorkflowValidator workflowValidator;
    private final ChoiceMappingService choiceMappingService;

    public WorkflowResponse createWorkflow(String userId, WorkflowCreateRequest request) {
        Workflow workflow = Workflow.builder()
                .name(request.getName())
                .description(request.getDescription())
                .userId(userId)
                .nodes(request.getNodes() != null ? request.getNodes() : new ArrayList<>())
                .edges(request.getEdges() != null ? request.getEdges() : new ArrayList<>())
                .trigger(request.getTrigger())
                .build();

        List<ValidationWarning> warnings = workflowValidator.validate(workflow);
        Workflow saved = workflowRepository.save(workflow);
        return WorkflowResponse.from(saved, warnings);
    }

    public List<WorkflowResponse> getWorkflowsByUserId(String userId) {
        List<Workflow> workflows = workflowRepository
                .findByUserIdOrSharedWithContainingOrderByUpdatedAtDesc(userId, userId);

        return workflows.stream()
                .map(WorkflowResponse::from)
                .toList();
    }

    public WorkflowResponse getWorkflowById(String userId, String workflowId) {
        Workflow workflow = findWorkflowOrThrow(workflowId);
        verifyAccess(workflow, userId);
        return WorkflowResponse.from(workflow);
    }

    public WorkflowResponse updateWorkflow(String userId, String workflowId, WorkflowUpdateRequest request) {
        Workflow workflow = findWorkflowOrThrow(workflowId);
        verifyAccess(workflow, userId);

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

        List<ValidationWarning> warnings = workflowValidator.validate(workflow);
        Workflow saved = workflowRepository.save(workflow);
        return WorkflowResponse.from(saved, warnings);
    }

    public void deleteWorkflow(String userId, String workflowId) {
        Workflow workflow = findWorkflowOrThrow(workflowId);
        verifyOwnership(workflow, userId);
        workflowRepository.delete(workflow);
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

        return choiceMappingService.onUserSelect(selectedOptionId, dataType);
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
                    .build();
            workflow.getEdges().add(edge);
        }

        List<ValidationWarning> warnings = workflowValidator.validate(workflow);
        Workflow saved = workflowRepository.save(workflow);
        return WorkflowResponse.from(saved, warnings);
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
        return WorkflowResponse.from(saved, warnings);
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
        return WorkflowResponse.from(saved, warnings);
    }

    private void collectDownstreamNodes(Workflow workflow, String startNodeId, Set<String> collected) {
        collected.add(startNodeId);
        for (EdgeDefinition edge : workflow.getEdges()) {
            if (edge.getSource().equals(startNodeId) && !collected.contains(edge.getTarget())) {
                collectDownstreamNodes(workflow, edge.getTarget(), collected);
            }
        }
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
}
