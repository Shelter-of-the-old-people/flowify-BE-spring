package org.github.flowify.workflow.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.Map;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NodeDefinition {

    private String id;
    private String category;
    private String type;
    private String label;
    private Map<String, Object> config;
    private Position position;
    private String dataType;
    private String outputDataType;
    private String role;
    private boolean authWarning;
}