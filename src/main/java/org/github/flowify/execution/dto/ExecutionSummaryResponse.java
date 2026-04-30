package org.github.flowify.execution.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

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
}
