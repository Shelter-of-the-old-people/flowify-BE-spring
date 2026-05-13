package org.github.flowify.execution.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class ExecutionCompleteRequest {

    private String status;
    private Map<String, Object> output;
    private Long durationMs;
    private String error;
    private List<NodeStateUpdateRequest> nodeStateUpdates;
}
