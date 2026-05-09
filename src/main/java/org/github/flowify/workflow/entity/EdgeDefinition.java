package org.github.flowify.workflow.entity;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class EdgeDefinition {

    private String id;
    private String source;
    private String target;
    private String label;
    private String sourceHandle;
    private String targetHandle;
}
