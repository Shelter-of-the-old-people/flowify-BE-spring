package org.github.flowify.workflow;

import org.github.flowify.catalog.service.CatalogService;
import org.github.flowify.catalog.service.NodeLifecycleService;
import org.github.flowify.common.dto.PageResponse;
import org.github.flowify.common.exception.BusinessException;
import org.github.flowify.common.exception.ErrorCode;
import org.github.flowify.execution.entity.WorkflowExecution;
import org.github.flowify.execution.repository.ExecutionRepository;
import org.github.flowify.workflow.dto.NodeAddRequest;
import org.github.flowify.workflow.dto.NodeStatusResponse;
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
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorkflowServiceTest {

    @Mock
    private WorkflowRepository workflowRepository;
    @Mock
    private ExecutionRepository executionRepository;
    @Mock
    private WorkflowValidator workflowValidator;
    @Mock
    private ChoiceMappingService choiceMappingService;
    @Mock
    private NodeLifecycleService nodeLifecycleService;
    @Mock
    private CatalogService catalogService;
    @Mock
    private ApplicationEventPublisher eventPublisher;

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
    @DisplayName("workflow list status is resolved from latest execution and schedule active state")
    void getWorkflowPage_resolvesListStatus() {
        List<Workflow> workflows = List.of(
                manualWorkflow("manual-no-exec"),
                manualWorkflow("manual-running"),
                manualWorkflow("manual-pending"),
                manualWorkflow("manual-success"),
                manualWorkflow("manual-failed"),
                scheduleWorkflow("schedule-active", true),
                scheduleWorkflow("schedule-inactive", false),
                scheduleWorkflow("schedule-inactive-running", false)
        );

        when(workflowRepository.findListProjectionsByUserIdOrSharedWith(eq("user123"), eq("user123"), any(Pageable.class)))
                .thenReturn(new PageImpl<>(listProjections(workflows), PageRequest.of(0, 20,
                        Sort.by(Sort.Direction.DESC, "updatedAt")), workflows.size()));
        when(executionRepository.findLatestByWorkflowIdIn(anyCollection()))
                .thenReturn(List.of(
                        execution("manual-running", "running"),
                        execution("manual-pending", "pending"),
                        execution("manual-success", "success"),
                        execution("manual-failed", "failed"),
                        execution("schedule-inactive-running", "running")
                ));

        PageResponse<WorkflowListItemResponse> response = workflowService.getWorkflowPage("user123", 0, 20, "all");

        Map<String, String> statuses = response.getContent().stream()
                .collect(Collectors.toMap(WorkflowListItemResponse::getId, WorkflowListItemResponse::getListStatus));
        assertThat(statuses)
                .containsEntry("manual-no-exec", "stopped")
                .containsEntry("manual-running", "running")
                .containsEntry("manual-pending", "running")
                .containsEntry("manual-success", "stopped")
                .containsEntry("manual-failed", "stopped")
                .containsEntry("schedule-active", "running")
                .containsEntry("schedule-inactive", "stopped")
                .containsEntry("schedule-inactive-running", "running");
    }

    @Test
    @DisplayName("workflow list returns an empty page when no workflows exist")
    void getWorkflowPage_emptyPage() {
        when(workflowRepository.findListProjectionsByUserIdOrSharedWith(eq("user123"), eq("user123"), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20,
                        Sort.by(Sort.Direction.DESC, "updatedAt")), 0));

        PageResponse<WorkflowListItemResponse> response = workflowService.getWorkflowPage("user123", 0, 20, "all");

        assertThat(response.getContent()).isEmpty();
        assertThat(response.getTotalElements()).isZero();
        assertThat(response.getTotalPages()).isZero();
        verify(executionRepository, never()).findLatestByWorkflowIdIn(anyCollection());
    }

    @Test
    @DisplayName("workflow list response includes graph summaries and safe warnings")
    void getWorkflowPage_includesSummaryAndWarnings() {
        NodeDefinition start = NodeDefinition.builder()
                .id("start")
                .category("service")
                .type("gmail")
                .label("New Email")
                .role("start")
                .config(Map.of("service", "gmail", "source_mode", "new_email", "isConfigured", true))
                .outputDataType("TEXT")
                .build();
        NodeDefinition end1 = NodeDefinition.builder()
                .id("end-1")
                .category("service")
                .type("discord")
                .label("Discord")
                .role("end")
                .config(Map.of("service", "discord", "isConfigured", false))
                .dataType("TEXT")
                .build();
        NodeDefinition end2 = NodeDefinition.builder()
                .id("end-2")
                .category("service")
                .type("gmail")
                .label("Gmail")
                .role("end")
                .config(Map.of("service", "gmail", "isConfigured", true))
                .dataType("TEXT")
                .build();
        Workflow workflow = manualWorkflow("summary-workflow");
        workflow.setNodes(new ArrayList<>(List.of(start, end1, end2)));
        workflow.setEdges(new ArrayList<>(List.of(
                EdgeDefinition.builder().source("start").target("end-1").build(),
                EdgeDefinition.builder().source("start").target("end-2").build()
        )));
        ValidationWarning warning = ValidationWarning.builder()
                .nodeId("end-1")
                .message("Data type mismatch")
                .sourceType("TEXT")
                .targetType("SPREADSHEET_DATA")
                .build();

        when(workflowRepository.findListProjectionsByUserIdOrSharedWith(eq("user123"), eq("user123"), any(Pageable.class)))
                .thenReturn(new PageImpl<>(listProjections(List.of(workflow)), PageRequest.of(0, 20,
                        Sort.by(Sort.Direction.DESC, "updatedAt")), 1));
        when(executionRepository.findLatestByWorkflowIdIn(anyCollection())).thenReturn(List.of());
        when(workflowValidator.collectListWarnings(workflow.getNodes(), workflow.getEdges())).thenReturn(List.of(warning));

        PageResponse<WorkflowListItemResponse> response = workflowService.getWorkflowPage("user123", 0, 20, "all");

        WorkflowListItemResponse item = response.getContent().get(0);
        assertThat(item.getSummary().getTotalNodeCount()).isEqualTo(3);
        assertThat(item.getSummary().getConfiguredNodeCount()).isEqualTo(2);
        assertThat(item.getSummary().getStartNode().getType()).isEqualTo("gmail");
        assertThat(item.getSummary().getStartNode().getSourceMode()).isEqualTo("new_email");
        assertThat(item.getSummary().getEndNodes()).hasSize(2);
        assertThat(item.getSummary().getEndNodes()).extracting("id").containsExactly("end-1", "end-2");
        assertThat(item.getWarnings()).containsExactly(warning);
    }

    @Test
    @DisplayName("workflow list status filter is applied before pagination")
    void getWorkflowPage_filtersBeforePagination() {
        List<Workflow> workflows = List.of(
                manualWorkflow("running-1"),
                manualWorkflow("stopped-1"),
                manualWorkflow("running-2")
        );

        when(workflowRepository.findRunningListProjectionsByUserIdOrSharedWith(eq("user123"), eq("user123"), any(Pageable.class)))
                .thenReturn(new PageImpl<>(listProjections(List.of(workflows.get(0))), PageRequest.of(0, 1,
                        Sort.by(Sort.Direction.DESC, "updatedAt")), 2))
                .thenReturn(new PageImpl<>(listProjections(List.of(workflows.get(2))), PageRequest.of(1, 1,
                        Sort.by(Sort.Direction.DESC, "updatedAt")), 2));
        when(workflowRepository.findStoppedListProjectionsByUserIdOrSharedWith(eq("user123"), eq("user123"), any(Pageable.class)))
                .thenReturn(new PageImpl<>(listProjections(List.of(workflows.get(1))), PageRequest.of(0, 20,
                        Sort.by(Sort.Direction.DESC, "updatedAt")), 1));
        when(workflowRepository.findListProjectionsByUserIdOrSharedWith(eq("user123"), eq("user123"), any(Pageable.class)))
                .thenReturn(new PageImpl<>(listProjections(workflows), PageRequest.of(0, 20,
                        Sort.by(Sort.Direction.DESC, "updatedAt")), workflows.size()));
        when(executionRepository.findLatestByWorkflowIdIn(anyCollection()))
                .thenReturn(List.of(
                        execution("running-1", "running"),
                        execution("stopped-1", "success"),
                        execution("running-2", "pending")
                ));

        PageResponse<WorkflowListItemResponse> firstPage = workflowService.getWorkflowPage("user123", 0, 1, "running");
        PageResponse<WorkflowListItemResponse> secondPage = workflowService.getWorkflowPage("user123", 1, 1, "running");
        PageResponse<WorkflowListItemResponse> stopped = workflowService.getWorkflowPage("user123", 0, 20, "stopped");
        PageResponse<WorkflowListItemResponse> invalidStatus = workflowService.getWorkflowPage("user123", 0, 20, "unknown");

        assertThat(firstPage.getContent()).extracting(WorkflowListItemResponse::getId).containsExactly("running-1");
        assertThat(firstPage.getTotalElements()).isEqualTo(2);
        assertThat(firstPage.getTotalPages()).isEqualTo(2);
        assertThat(secondPage.getContent()).extracting(WorkflowListItemResponse::getId).containsExactly("running-2");
        assertThat(stopped.getContent()).extracting(WorkflowListItemResponse::getId).containsExactly("stopped-1");
        assertThat(invalidStatus.getContent()).hasSize(3);
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
    @DisplayName("update workflow response includes node statuses")
    void updateWorkflow_includesNodeStatuses() {
        NodeDefinition node = NodeDefinition.builder()
                .id("middle_search")
                .category("integration")
                .type("google_sheets")
                .build();
        testWorkflow.setNodes(new ArrayList<>(List.of(node)));

        when(workflowRepository.findById("wf1")).thenReturn(Optional.of(testWorkflow));
        when(workflowValidator.validate(any(Workflow.class))).thenReturn(Collections.emptyList());
        when(workflowRepository.save(any(Workflow.class))).thenAnswer(inv -> inv.getArgument(0));
        when(nodeLifecycleService.evaluateAll(eq(testWorkflow.getNodes()), eq("user123"))).thenReturn(List.of(
                NodeStatusResponse.builder()
                        .nodeId("middle_search")
                        .configured(false)
                        .saveable(true)
                        .choiceable(false)
                        .executable(false)
                        .missingFields(List.of("config.search_value"))
                        .build()
        ));

        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        mapper.findAndRegisterModules();
        WorkflowUpdateRequest request = mapper.convertValue(java.util.Map.of(), WorkflowUpdateRequest.class);

        WorkflowResponse response = workflowService.updateWorkflow("user123", "wf1", request);

        assertThat(response.getNodeStatuses()).hasSize(1);
        assertThat(response.getNodeStatuses().get(0).getMissingFields()).contains("config.search_value");
    }

    @Test
    @DisplayName("AI generated workflow is applied to an empty workflow")
    void applyGeneratedWorkflow_success() {
        when(workflowRepository.findById("wf1")).thenReturn(Optional.of(testWorkflow));
        when(workflowValidator.validate(any(Workflow.class))).thenReturn(Collections.emptyList());
        when(workflowRepository.save(any(Workflow.class))).thenAnswer(inv -> inv.getArgument(0));
        when(nodeLifecycleService.evaluateAll(any(), eq("user123"))).thenReturn(List.of(
                NodeStatusResponse.builder()
                        .nodeId("start_1")
                        .configured(false)
                        .saveable(true)
                        .choiceable(false)
                        .executable(false)
                        .missingFields(List.of("config.target"))
                        .build(),
                NodeStatusResponse.builder()
                        .nodeId("sink_1")
                        .configured(false)
                        .saveable(true)
                        .choiceable(false)
                        .executable(false)
                        .missingFields(List.of("config.webhook_url"))
                        .build()
        ));

        WorkflowCreateRequest request = toCreateRequest(Map.of(
                "name", "AI PR Summary",
                "description", "Generated by AI",
                "nodes", List.of(
                        Map.of(
                                "id", "start_1",
                                "category", "trigger",
                                "type", "github",
                                "role", "start",
                                "position", Map.of("x", 0, "y", 0),
                                "config", Map.of("service", "github", "source_mode", "new_pr"),
                                "outputDataType", "GITHUB_PULL_REQUEST",
                                "authWarning", false
                        ),
                        Map.of(
                                "id", "sink_1",
                                "category", "integration",
                                "type", "discord",
                                "role", "end",
                                "position", Map.of("x", 360, "y", 0),
                                "config", Map.of("service", "discord", "isConfigured", false),
                                "dataType", "GITHUB_PULL_REQUEST",
                                "authWarning", false
                        )
                ),
                "edges", List.of(
                        Map.of("id", "edge_start_sink", "source", "start_1", "target", "sink_1")
                ),
                "trigger", Map.of("type", "manual", "config", Map.of())
        ));

        WorkflowResponse response = workflowService.applyGeneratedWorkflow("user123", "wf1", request);

        assertThat(response.getId()).isEqualTo("wf1");
        assertThat(response.getName()).isEqualTo("AI PR Summary");
        assertThat(response.getDescription()).isEqualTo("Generated by AI");
        assertThat(response.getTrigger().getType()).isEqualTo("manual");
        assertThat(response.getNodes()).hasSize(2);
        assertThat(response.getEdges()).hasSize(1);
        assertThat(response.getNodeStatuses()).hasSize(2);
        verify(workflowRepository).save(testWorkflow);
    }

    @Test
    @DisplayName("AI generation target must be empty")
    void applyGeneratedWorkflow_rejectsNonEmptyWorkflow() {
        testWorkflow.setNodes(new ArrayList<>(List.of(
                NodeDefinition.builder()
                        .id("existing")
                        .category("trigger")
                        .type("gmail")
                        .role("start")
                        .build()
        )));

        when(workflowRepository.findById("wf1")).thenReturn(Optional.of(testWorkflow));

        WorkflowCreateRequest request = toCreateRequest(Map.of(
                "name", "Generated",
                "nodes", List.of(),
                "edges", List.of(),
                "trigger", Map.of("type", "manual", "config", Map.of())
        ));

        assertThatThrownBy(() -> workflowService.applyGeneratedWorkflow("user123", "wf1", request))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_REQUEST);

        verify(workflowRepository, never()).save(any(Workflow.class));
    }

    @Test
    @DisplayName("AI refined workflow preserves existing config for unchanged nodes")
    void applyRefinedWorkflow_preservesExistingConfigForUnchangedNodes() {
        testWorkflow.setName("Original workflow");
        testWorkflow.setDescription("Original description");
        testWorkflow.setNodes(new ArrayList<>(List.of(
                NodeDefinition.builder()
                        .id("start_1")
                        .category("service")
                        .type("gmail")
                        .role("start")
                        .config(Map.of(
                                "service", "gmail",
                                "source_mode", "new_email",
                                "target", "inbox",
                                "isConfigured", true))
                        .outputDataType("SINGLE_EMAIL")
                        .build(),
                NodeDefinition.builder()
                        .id("sink_1")
                        .category("service")
                        .type("discord")
                        .role("end")
                        .config(Map.of(
                                "service", "discord",
                                "webhook_url", "https://example.com/hook",
                                "isConfigured", true))
                        .dataType("TEXT")
                        .build()
        )));
        testWorkflow.setEdges(new ArrayList<>(List.of(
                EdgeDefinition.builder()
                        .id("edge_start_sink")
                        .source("start_1")
                        .target("sink_1")
                        .build()
        )));

        when(workflowRepository.findById("wf1")).thenReturn(Optional.of(testWorkflow));
        when(workflowValidator.validate(any(Workflow.class))).thenReturn(Collections.emptyList());
        when(workflowRepository.save(any(Workflow.class))).thenAnswer(inv -> inv.getArgument(0));
        when(nodeLifecycleService.evaluateAll(any(), eq("user123"))).thenReturn(List.of());

        WorkflowCreateRequest request = toCreateRequest(Map.of(
                "name", "Generated new name",
                "description", "Generated description",
                "nodes", List.of(
                        Map.of(
                                "id", "start_1",
                                "category", "service",
                                "type", "gmail",
                                "role", "start",
                                "position", Map.of("x", 0, "y", 0),
                                "config", Map.of(
                                        "service", "gmail",
                                        "source_mode", "new_email",
                                        "target", "",
                                        "isConfigured", false),
                                "outputDataType", "SINGLE_EMAIL",
                                "authWarning", false
                        ),
                        Map.of(
                                "id", "sink_1",
                                "category", "service",
                                "type", "discord",
                                "role", "end",
                                "position", Map.of("x", 360, "y", 0),
                                "config", Map.of(
                                        "service", "discord",
                                        "webhook_url", "",
                                        "message_template", "summary",
                                        "isConfigured", false),
                                "dataType", "TEXT",
                                "authWarning", false
                        )
                ),
                "edges", List.of(
                        Map.of("id", "edge_start_sink", "source", "start_1", "target", "sink_1")
                ),
                "trigger", Map.of("type", "manual", "config", Map.of())
        ));

        WorkflowResponse response = workflowService.applyRefinedWorkflow("user123", "wf1", request);

        assertThat(response.getName()).isEqualTo("Original workflow");
        assertThat(response.getDescription()).isEqualTo("Original description");
        assertThat(response.getNodes()).hasSize(2);
        assertThat(response.getNodes().get(0).getConfig())
                .containsEntry("target", "inbox")
                .doesNotContainKey("isConfigured");
        assertThat(response.getNodes().get(1).getConfig())
                .containsEntry("webhook_url", "https://example.com/hook")
                .containsEntry("message_template", "summary")
                .doesNotContainKey("isConfigured");
        verify(workflowRepository).save(testWorkflow);
    }

    @Test
    @DisplayName("AI refined workflow requires owner")
    void applyRefinedWorkflow_rejectsSharedViewer() {
        testWorkflow.setSharedWith(new ArrayList<>(List.of("viewer")));
        when(workflowRepository.findById("wf1")).thenReturn(Optional.of(testWorkflow));

        WorkflowCreateRequest request = toCreateRequest(Map.of(
                "name", "Generated",
                "nodes", List.of(),
                "edges", List.of(),
                "trigger", Map.of("type", "manual", "config", Map.of())
        ));

        assertThatThrownBy(() -> workflowService.applyRefinedWorkflow("viewer", "wf1", request))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).getErrorCode())
                .isEqualTo(ErrorCode.WORKFLOW_ACCESS_DENIED);

        verify(workflowRepository, never()).save(any(Workflow.class));
    }

    @Test
    @DisplayName("AI generation target requires workflow owner")
    void validateWorkflowGenerationTarget_rejectsSharedViewer() {
        testWorkflow.setSharedWith(new ArrayList<>(List.of("viewer")));
        when(workflowRepository.findById("wf1")).thenReturn(Optional.of(testWorkflow));

        assertThatThrownBy(() -> workflowService.validateWorkflowGenerationTarget("viewer", "wf1"))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).getErrorCode())
                .isEqualTo(ErrorCode.WORKFLOW_ACCESS_DENIED);

        verify(workflowRepository, never()).save(any(Workflow.class));
    }

    @Test
    @DisplayName("schedule activation validates executable workflow before saving")
    void updateWorkflow_scheduleActivationValidatesForExecution() {
        testWorkflow.setTrigger(validScheduleTrigger());
        testWorkflow.setActive(false);

        when(workflowRepository.findById("wf1")).thenReturn(Optional.of(testWorkflow));
        when(workflowValidator.validate(any(Workflow.class))).thenReturn(Collections.emptyList());
        when(workflowRepository.save(any(Workflow.class))).thenAnswer(inv -> inv.getArgument(0));

        WorkflowUpdateRequest request = toUpdateRequest(Map.of("active", true));

        workflowService.updateWorkflow("user123", "wf1", request);

        verify(workflowValidator).validateForExecution(
                eq(testWorkflow),
                eq(nodeLifecycleService),
                eq(catalogService),
                eq("user123"));
        verify(workflowRepository).save(testWorkflow);
    }

    @Test
    @DisplayName("schedule activation preflight failure prevents saving")
    void updateWorkflow_scheduleActivationPreflightFailurePreventsSaving() {
        testWorkflow.setTrigger(validScheduleTrigger());
        testWorkflow.setActive(false);

        when(workflowRepository.findById("wf1")).thenReturn(Optional.of(testWorkflow));
        when(workflowValidator.validate(any(Workflow.class))).thenReturn(Collections.emptyList());
        doThrow(new BusinessException(ErrorCode.PREFLIGHT_VALIDATION_FAILED, "missing required config"))
                .when(workflowValidator)
                .validateForExecution(any(Workflow.class), eq(nodeLifecycleService), eq(catalogService), eq("user123"));

        WorkflowUpdateRequest request = toUpdateRequest(Map.of("active", true));

        assertThatThrownBy(() -> workflowService.updateWorkflow("user123", "wf1", request))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).getErrorCode())
                .isEqualTo(ErrorCode.PREFLIGHT_VALIDATION_FAILED);

        verify(workflowRepository, never()).save(any(Workflow.class));
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    @DisplayName("schedule deactivation skips executable workflow validation")
    void updateWorkflow_scheduleDeactivationSkipsExecutionValidation() {
        testWorkflow.setTrigger(validScheduleTrigger());
        testWorkflow.setActive(true);

        when(workflowRepository.findById("wf1")).thenReturn(Optional.of(testWorkflow));
        when(workflowValidator.validate(any(Workflow.class))).thenReturn(Collections.emptyList());
        when(workflowRepository.save(any(Workflow.class))).thenAnswer(inv -> inv.getArgument(0));

        WorkflowUpdateRequest request = toUpdateRequest(Map.of("active", false));

        workflowService.updateWorkflow("user123", "wf1", request);

        verify(workflowValidator, never()).validateForExecution(
                any(Workflow.class),
                eq(nodeLifecycleService),
                eq(catalogService),
                eq("user123"));
    }

    @Test
    @DisplayName("delete workflow owner only")
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
    @DisplayName("노드 추가 시 이전 edge 분기 메타데이터를 저장한다")
    void addMiddleNode_savesPrevEdgeMetadata() {
        NodeDefinition prevNode = NodeDefinition.builder()
                .id("node_branch")
                .category("control")
                .type("condition")
                .outputDataType("FILE_LIST")
                .build();
        testWorkflow.setNodes(new ArrayList<>(List.of(prevNode)));

        when(workflowRepository.findById("wf1")).thenReturn(Optional.of(testWorkflow));
        when(workflowValidator.validate(any(Workflow.class))).thenReturn(Collections.emptyList());
        when(workflowRepository.save(any(Workflow.class))).thenAnswer(inv -> inv.getArgument(0));

        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        mapper.findAndRegisterModules();
        NodeAddRequest request = mapper.convertValue(
                java.util.Map.of(
                        "category", "processing",
                        "type", "loop",
                        "label", "PDF 처리",
                        "dataType", "FILE_LIST",
                        "outputDataType", "SINGLE_FILE",
                        "prevNodeId", "node_branch",
                        "prevEdgeLabel", "pdf",
                        "prevEdgeSourceHandle", "pdf",
                        "prevEdgeTargetHandle", "input"),
                NodeAddRequest.class);

        WorkflowResponse response = workflowService.addMiddleNode("user123", "wf1", request);

        assertThat(response.getEdges()).singleElement()
                .satisfies(edge -> {
                    assertThat(edge.getSource()).isEqualTo("node_branch");
                    assertThat(edge.getTarget()).startsWith("node_");
                    assertThat(edge.getLabel()).isEqualTo("pdf");
                    assertThat(edge.getSourceHandle()).isEqualTo("pdf");
                    assertThat(edge.getTargetHandle()).isEqualTo("input");
                });
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

    private List<WorkflowListProjection> listProjections(List<Workflow> workflows) {
        return workflows.stream()
                .map(this::listProjection)
                .toList();
    }

    private WorkflowListProjection listProjection(Workflow workflow) {
        return new WorkflowListProjection() {
            @Override
            public String getId() {
                return workflow.getId();
            }

            @Override
            public String getName() {
                return workflow.getName();
            }

            @Override
            public String getDescription() {
                return workflow.getDescription();
            }

            @Override
            public String getUserId() {
                return workflow.getUserId();
            }

            @Override
            public List<String> getSharedWith() {
                return workflow.getSharedWith();
            }

            @Override
            public boolean isTemplate() {
                return workflow.isTemplate();
            }

            @Override
            public String getTemplateId() {
                return workflow.getTemplateId();
            }

            @Override
            public boolean isActive() {
                return workflow.isActive();
            }

            @Override
            public TriggerConfig getTrigger() {
                return workflow.getTrigger();
            }

            @Override
            public Instant getCreatedAt() {
                return workflow.getCreatedAt();
            }

            @Override
            public Instant getUpdatedAt() {
                return workflow.getUpdatedAt();
            }

            @Override
            public String getLatestExecutionId() {
                return workflow.getLatestExecutionId();
            }

            @Override
            public String getLatestExecutionState() {
                return workflow.getLatestExecutionState();
            }

            @Override
            public Instant getLatestExecutionStartedAt() {
                return workflow.getLatestExecutionStartedAt();
            }

            @Override
            public Instant getLatestExecutionFinishedAt() {
                return workflow.getLatestExecutionFinishedAt();
            }

            @Override
            public List<NodeDefinition> getNodes() {
                return workflow.getNodes();
            }

            @Override
            public List<EdgeDefinition> getEdges() {
                return workflow.getEdges();
            }
        };
    }

    private Workflow manualWorkflow(String id) {
        return Workflow.builder()
                .id(id)
                .name(id)
                .userId("user123")
                .sharedWith(new ArrayList<>())
                .nodes(new ArrayList<>())
                .edges(new ArrayList<>())
                .trigger(TriggerConfig.builder().type("manual").config(Map.of()).build())
                .isActive(true)
                .build();
    }

    private Workflow scheduleWorkflow(String id, boolean active) {
        return Workflow.builder()
                .id(id)
                .name(id)
                .userId("user123")
                .sharedWith(new ArrayList<>())
                .nodes(new ArrayList<>())
                .edges(new ArrayList<>())
                .trigger(TriggerConfig.builder().type("schedule").config(Map.of()).build())
                .isActive(active)
                .build();
    }

    private TriggerConfig validScheduleTrigger() {
        return TriggerConfig.builder()
                .type("schedule")
                .config(Map.of(
                        "schedule_mode", "interval",
                        "cron", "0 0 */4 * * *",
                        "interval_hours", 4))
                .build();
    }

    private WorkflowUpdateRequest toUpdateRequest(Map<String, Object> payload) {
        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        mapper.findAndRegisterModules();
        return mapper.convertValue(payload, WorkflowUpdateRequest.class);
    }

    private WorkflowCreateRequest toCreateRequest(Map<String, Object> payload) {
        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        mapper.findAndRegisterModules();
        return mapper.convertValue(payload, WorkflowCreateRequest.class);
    }

    private WorkflowExecution execution(String workflowId, String state) {
        return WorkflowExecution.builder()
                .id("exec-" + workflowId)
                .workflowId(workflowId)
                .userId("user123")
                .state(state)
                .startedAt(Instant.parse("2026-01-01T00:00:00Z"))
                .build();
    }
}
