package org.github.flowify.dashboard.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.github.flowify.catalog.service.NodeLifecycleService;
import org.github.flowify.dashboard.dto.DashboardIssueItemResponse;
import org.github.flowify.dashboard.dto.DashboardIssueResponse;
import org.github.flowify.dashboard.dto.DashboardMetricsResponse;
import org.github.flowify.dashboard.dto.DashboardSummaryResponse;
import org.github.flowify.execution.entity.NodeLog;
import org.github.flowify.execution.entity.WorkflowExecution;
import org.github.flowify.execution.repository.ExecutionRepository;
import org.github.flowify.workflow.dto.NodeStatusResponse;
import org.github.flowify.workflow.entity.NodeDefinition;
import org.github.flowify.workflow.entity.Workflow;
import org.github.flowify.workflow.repository.WorkflowRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
@Slf4j
public class DashboardService {

    private static final ZoneId DASHBOARD_ZONE = ZoneId.of("Asia/Seoul");
    private static final int ISSUE_LIMIT = 5;
    private static final List<String> FAILED_STATES = List.of("failed", "rollback_available");

    private final WorkflowRepository workflowRepository;
    private final ExecutionRepository executionRepository;
    private final NodeLifecycleService nodeLifecycleService;

    public DashboardSummaryResponse getSummary(String userId) {
        LocalDate today = LocalDate.now(DASHBOARD_ZONE);
        Instant todayStart = today.atStartOfDay(DASHBOARD_ZONE).toInstant();
        Instant tomorrowStart = today.plusDays(1).atStartOfDay(DASHBOARD_ZONE).toInstant();

        List<Workflow> workflows = workflowRepository
                .findByUserIdOrSharedWithContainingOrderByUpdatedAtDesc(userId, userId);

        Map<String, Workflow> workflowsById = workflows.stream()
                .collect(Collectors.toMap(Workflow::getId, Function.identity(), (left, right) -> left));

        return DashboardSummaryResponse.builder()
                .metrics(buildMetrics(userId, todayStart, tomorrowStart))
                .issues(buildIssues(userId, workflows, workflowsById, todayStart, tomorrowStart))
                .services(List.of())
                .build();
    }

    private DashboardMetricsResponse buildMetrics(String userId, Instant todayStart, Instant tomorrowStart) {
        long todayProcessedCount = executionRepository
                .countByUserIdAndFinishedAtBetween(userId, todayStart, tomorrowStart);
        long totalProcessedCount = executionRepository.countByUserIdAndFinishedAtIsNotNull(userId);
        long totalDurationMs = executionRepository.sumDurationMsByUserId(userId)
                .map(ExecutionRepository.DurationSumProjection::getTotalDurationMs)
                .orElse(0L);

        return DashboardMetricsResponse.builder()
                .todayProcessedCount(todayProcessedCount)
                .totalProcessedCount(totalProcessedCount)
                .totalDurationMs(totalDurationMs)
                .build();
    }

    private List<DashboardIssueResponse> buildIssues(String userId,
                                                     List<Workflow> workflows,
                                                     Map<String, Workflow> workflowsById,
                                                     Instant todayStart,
                                                     Instant tomorrowStart) {
        return Stream.concat(
                        buildFailedExecutionIssues(userId, workflowsById, todayStart, tomorrowStart).stream(),
                        buildNotExecutableWorkflowIssues(userId, workflows).stream()
                )
                .sorted(Comparator.comparing(
                        DashboardIssueResponse::getOccurredAt,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(ISSUE_LIMIT)
                .toList();
    }

    private List<DashboardIssueResponse> buildFailedExecutionIssues(String userId,
                                                                    Map<String, Workflow> workflowsById,
                                                                    Instant todayStart,
                                                                    Instant tomorrowStart) {
        Map<String, WorkflowExecution> executionsById = new LinkedHashMap<>();

        executionRepository
                .findByUserIdAndStateInAndFinishedAtBetween(userId, FAILED_STATES, todayStart, tomorrowStart)
                .forEach(execution -> executionsById.put(execution.getId(), execution));

        executionRepository.findTop50ByUserIdOrderByStartedAtDesc(userId).stream()
                .filter(this::isFailedState)
                .filter(execution -> isWithinToday(resolveOccurredAt(execution), todayStart, tomorrowStart))
                .forEach(execution -> executionsById.putIfAbsent(execution.getId(), execution));

        return executionsById.values().stream()
                .map(execution -> buildFailedExecutionIssue(execution, workflowsById.get(execution.getWorkflowId())))
                .toList();
    }

    private DashboardIssueResponse buildFailedExecutionIssue(WorkflowExecution execution, Workflow workflow) {
        Instant occurredAt = resolveOccurredAt(execution);
        List<DashboardIssueItemResponse> items = buildFailedExecutionItems(execution, workflow);

        return DashboardIssueResponse.builder()
                .id(execution.getId())
                .type("EXECUTION_FAILED")
                .workflowId(execution.getWorkflowId())
                .workflowName(workflow != null ? workflow.getName() : null)
                .isActive(workflow != null && workflow.isActive())
                .startService(getServiceFromEndpointNode(workflow, true))
                .endService(getServiceFromEndpointNode(workflow, false))
                .occurredAt(occurredAt)
                .message(firstNonBlank(execution.getError(), "Workflow execution failed"))
                .items(items)
                .build();
    }

    private List<DashboardIssueItemResponse> buildFailedExecutionItems(WorkflowExecution execution,
                                                                       Workflow workflow) {
        List<NodeLog> nodeLogs = execution.getNodeLogs() != null ? execution.getNodeLogs() : List.of();
        List<DashboardIssueItemResponse> items = nodeLogs.stream()
                .filter(nodeLog -> "failed".equals(nodeLog.getStatus()))
                .map(nodeLog -> DashboardIssueItemResponse.builder()
                        .id(execution.getId() + "-" + nodeLog.getNodeId())
                        .service(getServiceForNode(workflow, nodeLog.getNodeId()))
                        .message(getNodeErrorMessage(nodeLog, execution.getError()))
                        .build())
                .toList();

        if (!items.isEmpty()) {
            return items;
        }

        return List.of(DashboardIssueItemResponse.builder()
                .id(execution.getId() + "-execution")
                .service(null)
                .message(firstNonBlank(execution.getError(), "Workflow execution failed"))
                .build());
    }

    private List<DashboardIssueResponse> buildNotExecutableWorkflowIssues(String userId, List<Workflow> workflows) {
        List<DashboardIssueResponse> issues = new ArrayList<>();

        for (Workflow workflow : workflows) {
            if (issues.size() >= ISSUE_LIMIT) {
                break;
            }

            List<NodeStatusResponse> statuses = evaluateWorkflowStatuses(userId, workflow);
            List<NodeStatusResponse> notExecutableStatuses = statuses.stream()
                    .filter(status -> !status.isExecutable())
                    .toList();

            if (notExecutableStatuses.isEmpty()) {
                continue;
            }

            List<DashboardIssueItemResponse> items = notExecutableStatuses.stream()
                    .map(status -> DashboardIssueItemResponse.builder()
                            .id(workflow.getId() + "-" + status.getNodeId())
                            .service(getServiceForNode(workflow, status.getNodeId()))
                            .message(buildNotExecutableMessage(workflow, status))
                            .build())
                    .toList();

            issues.add(DashboardIssueResponse.builder()
                    .id(workflow.getId() + "-not-executable")
                    .type("WORKFLOW_NOT_EXECUTABLE")
                    .workflowId(workflow.getId())
                    .workflowName(workflow.getName())
                    .isActive(workflow.isActive())
                    .startService(getServiceFromEndpointNode(workflow, true))
                    .endService(getServiceFromEndpointNode(workflow, false))
                    .occurredAt(workflow.getUpdatedAt())
                    .message("Workflow has non-executable nodes")
                    .items(items)
                    .build());
        }

        return issues;
    }

    private List<NodeStatusResponse> evaluateWorkflowStatuses(String userId, Workflow workflow) {
        try {
            return nodeLifecycleService.evaluateAll(workflow.getNodes(), userId);
        } catch (Exception e) {
            log.warn("Dashboard node status evaluation failed. userId={}, workflowId={}",
                    userId, workflow.getId(), e);
            return List.of();
        }
    }

    private boolean isFailedState(WorkflowExecution execution) {
        return execution != null && FAILED_STATES.contains(execution.getState());
    }

    private boolean isWithinToday(Instant occurredAt, Instant todayStart, Instant tomorrowStart) {
        return occurredAt != null && !occurredAt.isBefore(todayStart) && occurredAt.isBefore(tomorrowStart);
    }

    private Instant resolveOccurredAt(WorkflowExecution execution) {
        if (execution == null) {
            return null;
        }
        return execution.getFinishedAt() != null ? execution.getFinishedAt() : execution.getStartedAt();
    }

    private String getServiceFromEndpointNode(Workflow workflow, boolean start) {
        if (workflow == null || workflow.getNodes() == null || workflow.getNodes().isEmpty()) {
            return null;
        }

        List<NodeDefinition> nodes = workflow.getNodes();
        Optional<NodeDefinition> endpointNode = nodes.stream()
                .filter(node -> start ? "start".equals(node.getRole()) : "end".equals(node.getRole()))
                .findFirst();

        return endpointNode
                .orElseGet(() -> start ? nodes.get(0) : nodes.get(nodes.size() - 1))
                .getType();
    }

    private String getServiceForNode(Workflow workflow, String nodeId) {
        if (workflow == null || workflow.getNodes() == null || nodeId == null) {
            return null;
        }

        return workflow.getNodes().stream()
                .filter(node -> nodeId.equals(node.getId()))
                .findFirst()
                .map(NodeDefinition::getType)
                .orElse(null);
    }

    private String buildNotExecutableMessage(Workflow workflow, NodeStatusResponse status) {
        String nodeLabel = getNodeLabel(workflow, status.getNodeId());
        String missingFields = status.getMissingFields() == null || status.getMissingFields().isEmpty()
                ? null
                : String.join(", ", status.getMissingFields());

        if (missingFields == null) {
            return nodeLabel + " is not executable";
        }
        return nodeLabel + " is not executable: " + missingFields;
    }

    private String getNodeLabel(Workflow workflow, String nodeId) {
        if (workflow == null || workflow.getNodes() == null || nodeId == null) {
            return "Node";
        }

        return workflow.getNodes().stream()
                .filter(node -> nodeId.equals(node.getId()))
                .findFirst()
                .map(node -> firstNonBlank(node.getLabel(), node.getType(), node.getId(), "Node"))
                .orElse("Node");
    }

    private String getNodeErrorMessage(NodeLog nodeLog, String fallback) {
        if (nodeLog.getError() != null && isNotBlank(nodeLog.getError().getMessage())) {
            return nodeLog.getError().getMessage();
        }
        return firstNonBlank(fallback, "Node execution failed");
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (isNotBlank(value)) {
                return value;
            }
        }
        return null;
    }

    private boolean isNotBlank(String value) {
        return value != null && !value.isBlank();
    }

}
