package org.github.flowify.workflow.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EdgeDefinition {

    private String id;
    private String source;
    private String target;
}