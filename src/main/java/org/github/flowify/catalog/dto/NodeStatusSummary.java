package org.github.flowify.catalog.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
@AllArgsConstructor
public class NodeStatusSummary {

    private final boolean configured;
    private final boolean executable;
    private final List<String> missingFields;
}
