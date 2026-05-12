package org.github.flowify.execution.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import org.github.flowify.execution.entity.NodeLog;
import org.github.flowify.execution.entity.WorkflowExecution;

import java.time.Instant;
import java.util.List;

@Getter
@Builder
@AllArgsConstructor
public class ExecutionSummaryResponse {

    private String id;
    private String workflowId;
    private String state;
    private Instant startedAt;
    private Instant finishedAt;
    private Long durationMs;
    private String errorMessage;
    private int nodeCount;
    private int completedNodeCount;

    public static ExecutionSummaryResponse from(WorkflowExecution execution) {
        List<NodeLog> logs = execution.getNodeLogs();
        int nodeCount = logs != null ? logs.size() : 0;
        int completedNodeCount = logs != null
                ? (int) logs.stream().filter(log -> "success".equals(log.getStatus())).count()
                : 0;

        return ExecutionSummaryResponse.builder()
                .id(execution.getId())
                .workflowId(execution.getWorkflowId())
                .state(execution.getState())
                .startedAt(execution.getStartedAt())
                .finishedAt(execution.getFinishedAt())
                .durationMs(execution.getDurationMs())
                .errorMessage(execution.getError())
                .nodeCount(nodeCount)
                .completedNodeCount(completedNodeCount)
                .build();
    }
}
