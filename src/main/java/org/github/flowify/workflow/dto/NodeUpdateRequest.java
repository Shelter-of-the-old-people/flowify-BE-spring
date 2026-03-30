package org.github.flowify.workflow.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.github.flowify.workflow.entity.Position;

import java.util.Map;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class NodeUpdateRequest {

    private String category;
    private String type;
    private Map<String, Object> config;
    private Position position;
    private String dataType;
    private String outputDataType;
    private String role;
    private Boolean authWarning;
}
