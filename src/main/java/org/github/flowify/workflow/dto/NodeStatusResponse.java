package org.github.flowify.workflow.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
@AllArgsConstructor
public class NodeStatusResponse {

    private final String nodeId;
    private final boolean configured;
    private final boolean saveable;
    private final boolean choiceable;
    private final boolean executable;
    private final List<String> missingFields;
}
