package org.github.flowify.workflow;

import org.github.flowify.common.exception.BusinessException;
import org.github.flowify.common.exception.ErrorCode;
import org.github.flowify.workflow.dto.ValidationWarning;
import org.github.flowify.workflow.dto.WorkflowCreateRequest;
import org.github.flowify.workflow.dto.WorkflowResponse;
import org.github.flowify.workflow.dto.WorkflowUpdateRequest;
import org.github.flowify.workflow.entity.EdgeDefinition;
import org.github.flowify.workflow.entity.NodeDefinition;
import org.github.flowify.workflow.entity.Workflow;
import org.github.flowify.workflow.repository.WorkflowRepository;
import org.github.flowify.workflow.service.WorkflowService;
import org.github.flowify.workflow.service.WorkflowValidator;
import org.github.flowify.workflow.service.choice.ChoiceMappingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorkflowServiceTest {

    @Mock
    private WorkflowRepository workflowRepository;
    @Mock
    private WorkflowValidator workflowValidator;
    @Mock
    private ChoiceMappingService choiceMappingService;

    @InjectMocks
    private WorkflowService workflowService;

    private Workflow testWorkflow;

    @BeforeEach
    void setUp() {
        testWorkflow = Workflow.builder()
                .id("wf1")
                .name("테스트 워크플로우")
                .description("테스트용")
                .userId("user123")
                .nodes(new ArrayList<>())
                .edges(new ArrayList<>())
                .sharedWith(new ArrayList<>())
                .build();
    }

    @Test
    @DisplayName("워크플로우 생성 성공")
    void createWorkflow_success() {
        when(workflowValidator.validate(any(Workflow.class))).thenReturn(Collections.emptyList());
        when(workflowRepository.save(any(Workflow.class))).thenAnswer(inv -> {
            Workflow wf = inv.getArgument(0);
            wf.setId("wf-new");
            return wf;
        });

        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        mapper.findAndRegisterModules();
        WorkflowCreateRequest request = mapper.convertValue(
                java.util.Map.of("name", "새 워크플로우", "description", "설명"),
                WorkflowCreateRequest.class);

        WorkflowResponse response = workflowService.createWorkflow("user123", request);

        assertThat(response.getName()).isEqualTo("새 워크플로우");
        verify(workflowValidator).validate(any(Workflow.class));
    }

    @Test
    @DisplayName("워크플로우 목록 조회")
    void getWorkflowsByUserId() {
        when(workflowRepository.findByUserIdOrSharedWithContainingOrderByUpdatedAtDesc(
                eq("user123"), eq("user123")))
                .thenReturn(List.of(testWorkflow));

        List<WorkflowResponse> result = workflowService.getWorkflowsByUserId("user123");

        assertThat(result).hasSize(1);
    }

    @Test
    @DisplayName("워크플로우 상세 조회 - 소유자")
    void getWorkflowById_owner() {
        when(workflowRepository.findById("wf1")).thenReturn(Optional.of(testWorkflow));

        WorkflowResponse response = workflowService.getWorkflowById("user123", "wf1");

        assertThat(response.getId()).isEqualTo("wf1");
    }

    @Test
    @DisplayName("워크플로우 상세 조회 - 공유된 사용자")
    void getWorkflowById_sharedUser() {
        testWorkflow.setSharedWith(List.of("user456"));
        when(workflowRepository.findById("wf1")).thenReturn(Optional.of(testWorkflow));

        WorkflowResponse response = workflowService.getWorkflowById("user456", "wf1");

        assertThat(response.getId()).isEqualTo("wf1");
    }

    @Test
    @DisplayName("워크플로우 조회 - 접근 권한 없음")
    void getWorkflowById_accessDenied() {
        when(workflowRepository.findById("wf1")).thenReturn(Optional.of(testWorkflow));

        assertThatThrownBy(() -> workflowService.getWorkflowById("other-user", "wf1"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.WORKFLOW_ACCESS_DENIED);
    }

    @Test
    @DisplayName("존재하지 않는 워크플로우 조회")
    void getWorkflowById_notFound() {
        when(workflowRepository.findById("unknown")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> workflowService.getWorkflowById("user123", "unknown"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.WORKFLOW_NOT_FOUND);
    }

    @Test
    @DisplayName("워크플로우 수정 성공")
    void updateWorkflow_success() {
        when(workflowRepository.findById("wf1")).thenReturn(Optional.of(testWorkflow));
        when(workflowValidator.validate(any(Workflow.class))).thenReturn(Collections.emptyList());
        when(workflowRepository.save(any(Workflow.class))).thenAnswer(inv -> inv.getArgument(0));

        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        mapper.findAndRegisterModules();
        WorkflowUpdateRequest request = mapper.convertValue(
                java.util.Map.of("name", "수정된 이름"), WorkflowUpdateRequest.class);

        WorkflowResponse response = workflowService.updateWorkflow("user123", "wf1", request);

        assertThat(response.getName()).isEqualTo("수정된 이름");
    }

    @Test
    @DisplayName("워크플로우 삭제 - 소유자만 가능")
    void deleteWorkflow_ownerOnly() {
        when(workflowRepository.findById("wf1")).thenReturn(Optional.of(testWorkflow));

        workflowService.deleteWorkflow("user123", "wf1");

        verify(workflowRepository).delete(testWorkflow);
    }

    @Test
    @DisplayName("워크플로우 삭제 - 비소유자 접근 거부")
    void deleteWorkflow_notOwner() {
        when(workflowRepository.findById("wf1")).thenReturn(Optional.of(testWorkflow));

        assertThatThrownBy(() -> workflowService.deleteWorkflow("other-user", "wf1"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.WORKFLOW_ACCESS_DENIED);
    }

    @Test
    @DisplayName("워크플로우 공유 설정")
    void shareWorkflow() {
        when(workflowRepository.findById("wf1")).thenReturn(Optional.of(testWorkflow));

        workflowService.shareWorkflow("user123", "wf1", List.of("user456", "user789"));

        assertThat(testWorkflow.getSharedWith()).containsExactly("user456", "user789");
        verify(workflowRepository).save(testWorkflow);
    }

    @Test
    @DisplayName("노드 삭제 시 캐스케이드 동작")
    void deleteNodeCascade() {
        NodeDefinition node1 = NodeDefinition.builder().id("node_1").category("ai").type("AI").build();
        NodeDefinition node2 = NodeDefinition.builder().id("node_2").category("ai").type("AI").build();
        NodeDefinition node3 = NodeDefinition.builder().id("node_3").category("ai").type("AI").build();
        EdgeDefinition edge1 = EdgeDefinition.builder().source("node_1").target("node_2").build();
        EdgeDefinition edge2 = EdgeDefinition.builder().source("node_2").target("node_3").build();

        testWorkflow.setNodes(new ArrayList<>(List.of(node1, node2, node3)));
        testWorkflow.setEdges(new ArrayList<>(List.of(edge1, edge2)));

        when(workflowRepository.findById("wf1")).thenReturn(Optional.of(testWorkflow));
        when(workflowValidator.validate(any(Workflow.class))).thenReturn(Collections.emptyList());
        when(workflowRepository.save(any(Workflow.class))).thenAnswer(inv -> inv.getArgument(0));

        WorkflowResponse response = workflowService.deleteNodeCascade("user123", "wf1", "node_2");

        // node_2와 node_3이 삭제되고 node_1만 남아야 함
        assertThat(response.getNodes()).hasSize(1);
        assertThat(response.getNodes().get(0).getId()).isEqualTo("node_1");
        assertThat(response.getEdges()).isEmpty();
    }
}
