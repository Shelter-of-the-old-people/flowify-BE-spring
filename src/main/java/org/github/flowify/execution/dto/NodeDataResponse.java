package org.github.flowify.execution.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import org.github.flowify.execution.entity.ErrorDetail;
import org.github.flowify.execution.entity.NodeSnapshot;

import java.time.Instant;
import java.util.Map;

@Getter
@Builder
@AllArgsConstructor
public class NodeDataResponse {

    private String executionId;
    private String workflowId;
    private String nodeId;
    private String status;
    private Map<String, Object> inputData;
    private Map<String, Object> outputData;
    private NodeSnapshot snapshot;
    private ErrorDetail error;
    private Instant startedAt;
    private Instant finishedAt;
    private boolean available;
    private String reason;
}
