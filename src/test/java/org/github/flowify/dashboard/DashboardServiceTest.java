package org.github.flowify.dashboard;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import org.github.flowify.catalog.service.NodeLifecycleService;
import org.github.flowify.dashboard.dto.DashboardIssueResponse;
import org.github.flowify.dashboard.dto.DashboardServiceResponse;
import org.github.flowify.dashboard.dto.DashboardSummaryResponse;
import org.github.flowify.dashboard.service.DashboardService;
import org.github.flowify.execution.entity.ErrorDetail;
import org.github.flowify.execution.entity.NodeLog;
import org.github.flowify.execution.entity.WorkflowExecution;
import org.github.flowify.execution.repository.ExecutionRepository;
import org.github.flowify.oauth.service.OAuthTokenService;
import org.github.flowify.workflow.dto.NodeStatusResponse;
import org.github.flowify.workflow.entity.NodeDefinition;
import org.github.flowify.workflow.entity.Workflow;
import org.github.flowify.workflow.repository.WorkflowRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DashboardServiceTest {

    private static final String USER_ID = "user123";
    private static final ZoneId DASHBOARD_ZONE = ZoneId.of("Asia/Seoul");

    @Mock
    private WorkflowRepository workflowRepository;
    @Mock
    private ExecutionRepository executionRepository;
    @Mock
    private OAuthTokenService oauthTokenService;
    @Mock
    private NodeLifecycleService nodeLifecycleService;

    @InjectMocks
    private DashboardService dashboardService;

    @Test
    @DisplayName("execution이 없으면 metric은 모두 0이다")
    void getSummary_withoutExecutions_returnsZeroMetrics() {
        mockEmptyDependencies();

        DashboardSummaryResponse response = dashboardService.getSummary(USER_ID);

        assertThat(response.getMetrics().getTodayProcessedCount()).isZero();
        assertThat(response.getMetrics().getTotalProcessedCount()).isZero();
        assertThat(response.getMetrics().getTotalDurationMs()).isZero();
    }

    @Test
    @DisplayName("Asia/Seoul 기준 오늘 완료된 실행만 todayProcessedCount에 포함한다")
    void getSummary_countsTodayCompletedExecutionsUsingAsiaSeoulRange() {
        when(workflowRepository.findByUserIdOrSharedWithContainingOrderByUpdatedAtDesc(USER_ID, USER_ID))
                .thenReturn(List.of());
        when(executionRepository.countByUserIdAndFinishedAtIsNotNull(USER_ID)).thenReturn(0L);
        when(executionRepository.countByUserIdAndFinishedAtBetween(eq(USER_ID), any(), any()))
                .thenReturn(1L);
        when(executionRepository.sumDurationMsByUserId(USER_ID)).thenReturn(Optional.empty());
        when(executionRepository.findByUserIdAndStateInAndFinishedAtBetween(eq(USER_ID), eq(List.of("failed", "rollback_available")),
                any(), any()))
                .thenReturn(List.of());
        when(executionRepository.findTop50ByUserIdOrderByStartedAtDesc(USER_ID)).thenReturn(List.of());
        when(oauthTokenService.getConnectedServices(USER_ID)).thenReturn(List.of());

        DashboardSummaryResponse response = dashboardService.getSummary(USER_ID);

        assertThat(response.getMetrics().getTodayProcessedCount()).isEqualTo(1);

        ArgumentCaptor<Instant> fromCaptor = ArgumentCaptor.forClass(Instant.class);
        ArgumentCaptor<Instant> toCaptor = ArgumentCaptor.forClass(Instant.class);
        org.mockito.Mockito.verify(executionRepository)
                .countByUserIdAndFinishedAtBetween(eq(USER_ID), fromCaptor.capture(), toCaptor.capture());

        LocalDate today = LocalDate.now(DASHBOARD_ZONE);
        assertThat(fromCaptor.getValue()).isEqualTo(today.atStartOfDay(DASHBOARD_ZONE).toInstant());
        assertThat(toCaptor.getValue()).isEqualTo(today.plusDays(1).atStartOfDay(DASHBOARD_ZONE).toInstant());
    }

    @Test
    @DisplayName("전체 완료 실행의 durationMs 합을 totalDurationMs로 반환한다")
    void getSummary_sumsTotalDurationMs() {
        mockEmptyDependencies();
        when(executionRepository.countByUserIdAndFinishedAtIsNotNull(USER_ID)).thenReturn(3L);
        when(executionRepository.sumDurationMsByUserId(USER_ID)).thenReturn(Optional.of(durationSum(3500L)));

        DashboardSummaryResponse response = dashboardService.getSummary(USER_ID);

        assertThat(response.getMetrics().getTotalProcessedCount()).isEqualTo(3);
        assertThat(response.getMetrics().getTotalDurationMs()).isEqualTo(3500L);
    }

    @Test
    @DisplayName("오늘 failed 상태 실행은 EXECUTION_FAILED issue로 반환한다")
    void getSummary_failedExecutionToday_returnsExecutionFailedIssue() {
        Workflow workflow = workflow("wf1", USER_ID, todayAt(8));
        WorkflowExecution failedExecution = execution("exec-failed", "wf1", "failed",
                todayAt(10), todayAt(10), 500L);
        failedExecution.setNodeLogs(List.of(failedNodeLog("node-start", "Gmail failed")));

        when(workflowRepository.findByUserIdOrSharedWithContainingOrderByUpdatedAtDesc(USER_ID, USER_ID))
                .thenReturn(List.of(workflow));
        when(executionRepository.countByUserIdAndFinishedAtIsNotNull(USER_ID)).thenReturn(0L);
        when(executionRepository.countByUserIdAndFinishedAtBetween(eq(USER_ID), any(), any())).thenReturn(0L);
        when(executionRepository.sumDurationMsByUserId(USER_ID)).thenReturn(Optional.empty());
        when(executionRepository.findByUserIdAndStateInAndFinishedAtBetween(eq(USER_ID), eq(List.of("failed", "rollback_available")),
                any(), any()))
                .thenReturn(List.of(failedExecution));
        when(executionRepository.findTop50ByUserIdOrderByStartedAtDesc(USER_ID)).thenReturn(List.of());
        when(nodeLifecycleService.evaluateAll(workflow.getNodes(), USER_ID)).thenReturn(List.of());
        when(oauthTokenService.getConnectedServices(USER_ID)).thenReturn(List.of());

        DashboardSummaryResponse response = dashboardService.getSummary(USER_ID);

        assertThat(response.getIssues())
                .extracting(DashboardIssueResponse::getType)
                .contains("EXECUTION_FAILED");
        DashboardIssueResponse issue = response.getIssues().get(0);
        assertThat(issue.getId()).isEqualTo("exec-failed");
        assertThat(issue.getWorkflowId()).isEqualTo("wf1");
        assertThat(issue.getMessage()).isEqualTo("Workflow execution failed");
        assertThat(issue.getItems()).hasSize(1);
        assertThat(issue.getItems().get(0).getService()).isEqualTo("gmail");
        assertThat(issue.getItems().get(0).getMessage()).isEqualTo("Gmail failed");
    }

    @Test
    @DisplayName("오늘 rollback_available 상태 실행도 EXECUTION_FAILED issue로 반환한다")
    void getSummary_rollbackAvailableExecutionToday_returnsExecutionFailedIssue() {
        Workflow workflow = workflow("wf1", USER_ID, todayAt(8));
        WorkflowExecution rollbackExecution = execution("exec-rollback", "wf1", "rollback_available",
                todayAt(10), todayAt(10), 500L);

        when(workflowRepository.findByUserIdOrSharedWithContainingOrderByUpdatedAtDesc(USER_ID, USER_ID))
                .thenReturn(List.of(workflow));
        when(executionRepository.countByUserIdAndFinishedAtIsNotNull(USER_ID)).thenReturn(0L);
        when(executionRepository.countByUserIdAndFinishedAtBetween(eq(USER_ID), any(), any())).thenReturn(0L);
        when(executionRepository.sumDurationMsByUserId(USER_ID)).thenReturn(Optional.empty());
        when(executionRepository.findByUserIdAndStateInAndFinishedAtBetween(eq(USER_ID), eq(List.of("failed", "rollback_available")),
                any(), any()))
                .thenReturn(List.of(rollbackExecution));
        when(executionRepository.findTop50ByUserIdOrderByStartedAtDesc(USER_ID)).thenReturn(List.of());
        when(nodeLifecycleService.evaluateAll(workflow.getNodes(), USER_ID)).thenReturn(List.of());
        when(oauthTokenService.getConnectedServices(USER_ID)).thenReturn(List.of());

        DashboardSummaryResponse response = dashboardService.getSummary(USER_ID);

        assertThat(response.getIssues())
                .extracting(DashboardIssueResponse::getType)
                .containsExactly("EXECUTION_FAILED");
        assertThat(response.getIssues().get(0).getId()).isEqualTo("exec-rollback");
    }

    @Test
    @DisplayName("V1에서는 owner/shared 목록에 포함된 실행 불가능 workflow를 WORKFLOW_NOT_EXECUTABLE issue로 반환한다")
    void getSummary_notExecutableOwnerOrSharedWorkflow_returnsNotExecutableIssue() {
        Workflow sharedWorkflow = workflow("wf-shared", "owner-user", todayAt(7));
        sharedWorkflow.setSharedWith(List.of(USER_ID));
        when(workflowRepository.findByUserIdOrSharedWithContainingOrderByUpdatedAtDesc(USER_ID, USER_ID))
                .thenReturn(List.of(sharedWorkflow));
        mockExecutionAndServiceDependenciesAsEmpty();
        when(nodeLifecycleService.evaluateAll(sharedWorkflow.getNodes(), USER_ID))
                .thenReturn(List.of(NodeStatusResponse.builder()
                        .nodeId("node-start")
                        .configured(false)
                        .saveable(true)
                        .choiceable(false)
                        .executable(false)
                        .missingFields(List.of("config.target"))
                        .build()));

        DashboardSummaryResponse response = dashboardService.getSummary(USER_ID);

        assertThat(response.getIssues())
                .extracting(DashboardIssueResponse::getType)
                .containsExactly("WORKFLOW_NOT_EXECUTABLE");
        DashboardIssueResponse issue = response.getIssues().get(0);
        assertThat(issue.getWorkflowId()).isEqualTo("wf-shared");
        assertThat(issue.getItems().get(0).getService()).isEqualTo("gmail");
        assertThat(issue.getItems().get(0).getMessage()).contains("config.target");
    }

    @Test
    @DisplayName("OAuth service summary에는 accessToken, refreshToken, secret 원문을 포함하지 않는다")
    void getSummary_serviceSummaryDoesNotExposeTokenValues() {
        mockEmptyDependencies();
        when(oauthTokenService.getConnectedServices(USER_ID))
                .thenReturn(List.of(Map.of(
                        "service", "gmail",
                        "connected", true,
                        "expiresAt", "2026-05-12T11:00:00Z",
                        "accessToken", "raw-access-token",
                        "refreshToken", "raw-refresh-token",
                        "secret", "raw-secret"
                )));

        DashboardSummaryResponse response = dashboardService.getSummary(USER_ID);

        DashboardServiceResponse service = response.getServices().get(0);
        assertThat(service.getService()).isEqualTo("gmail");
        assertThat(service.getAccountEmail()).isNull();

        Map<String, Object> serialized = new ObjectMapper().convertValue(
                service, new TypeReference<>() {
                });
        assertThat(serialized).doesNotContainKeys("accessToken", "refreshToken", "secret");
        assertThat(serialized.values()).doesNotContain("raw-access-token", "raw-refresh-token", "raw-secret");
    }

    @Test
    @DisplayName("workflow/execution/oauth 데이터가 비어 있어도 NPE 없이 빈 응답과 0 metric을 반환한다")
    void getSummary_emptyData_returnsEmptySummaryWithoutNpe() {
        mockEmptyDependencies();

        DashboardSummaryResponse response = dashboardService.getSummary(USER_ID);

        assertThat(response.getMetrics().getTodayProcessedCount()).isZero();
        assertThat(response.getMetrics().getTotalProcessedCount()).isZero();
        assertThat(response.getMetrics().getTotalDurationMs()).isZero();
        assertThat(response.getIssues()).isEmpty();
        assertThat(response.getServices()).isEmpty();
    }

    private void mockEmptyDependencies() {
        when(workflowRepository.findByUserIdOrSharedWithContainingOrderByUpdatedAtDesc(USER_ID, USER_ID))
                .thenReturn(List.of());
        mockExecutionAndServiceDependenciesAsEmpty();
    }

    private void mockExecutionAndServiceDependenciesAsEmpty() {
        when(executionRepository.countByUserIdAndFinishedAtIsNotNull(USER_ID)).thenReturn(0L);
        when(executionRepository.countByUserIdAndFinishedAtBetween(eq(USER_ID), any(), any())).thenReturn(0L);
        when(executionRepository.sumDurationMsByUserId(USER_ID)).thenReturn(Optional.empty());
        when(executionRepository.findByUserIdAndStateInAndFinishedAtBetween(eq(USER_ID), eq(List.of("failed", "rollback_available")),
                any(), any()))
                .thenReturn(List.of());
        when(executionRepository.findTop50ByUserIdOrderByStartedAtDesc(USER_ID)).thenReturn(List.of());
        when(oauthTokenService.getConnectedServices(USER_ID)).thenReturn(List.of());
    }

    private ExecutionRepository.DurationSumProjection durationSum(long totalDurationMs) {
        return () -> totalDurationMs;
    }

    private Workflow workflow(String id, String userId, Instant updatedAt) {
        NodeDefinition startNode = NodeDefinition.builder()
                .id("node-start")
                .role("start")
                .type("gmail")
                .label("Gmail")
                .build();
        NodeDefinition endNode = NodeDefinition.builder()
                .id("node-end")
                .role("end")
                .type("slack")
                .label("Slack")
                .build();

        return Workflow.builder()
                .id(id)
                .name("Workflow " + id)
                .userId(userId)
                .sharedWith(List.of())
                .nodes(List.of(startNode, endNode))
                .isActive(true)
                .updatedAt(updatedAt)
                .build();
    }

    private WorkflowExecution execution(String id, String workflowId, String state,
                                        Instant startedAt, Instant finishedAt, Long durationMs) {
        return WorkflowExecution.builder()
                .id(id)
                .workflowId(workflowId)
                .userId(USER_ID)
                .state(state)
                .startedAt(startedAt)
                .finishedAt(finishedAt)
                .durationMs(durationMs)
                .error("Workflow execution failed")
                .build();
    }

    private NodeLog failedNodeLog(String nodeId, String message) {
        return NodeLog.builder()
                .nodeId(nodeId)
                .status("failed")
                .error(ErrorDetail.builder()
                        .message(message)
                        .build())
                .build();
    }

    private Instant todayAt(int hour) {
        return LocalDate.now(DASHBOARD_ZONE).atTime(hour, 0).atZone(DASHBOARD_ZONE).toInstant();
    }
}
