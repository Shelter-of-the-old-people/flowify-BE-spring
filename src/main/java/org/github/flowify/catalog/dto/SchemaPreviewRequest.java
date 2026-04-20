package org.github.flowify.catalog.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.github.flowify.workflow.entity.EdgeDefinition;
import org.github.flowify.workflow.entity.NodeDefinition;

import java.util.List;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class SchemaPreviewRequest {

    private List<NodeDefinition> nodes;
    private List<EdgeDefinition> edges;
}
