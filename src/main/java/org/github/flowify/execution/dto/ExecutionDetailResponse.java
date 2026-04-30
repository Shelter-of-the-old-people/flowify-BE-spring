package org.github.flowify.execution.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import org.github.flowify.execution.entity.NodeLog;

import java.time.Instant;
import java.util.List;

@Getter
@Builder
@AllArgsConstructor
public class ExecutionDetailResponse {

    private String id;
    private String workflowId;
    private String state;
    private Instant startedAt;
    private Instant finishedAt;
    private Long durationMs;
    private String errorMessage;
    private List<NodeLog> nodeLogs;
}
