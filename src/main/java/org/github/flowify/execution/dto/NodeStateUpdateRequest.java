package org.github.flowify.execution.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.Map;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class NodeStateUpdateRequest {

    private String nodeId;
    private String service;
    private Map<String, Object> state;
}
