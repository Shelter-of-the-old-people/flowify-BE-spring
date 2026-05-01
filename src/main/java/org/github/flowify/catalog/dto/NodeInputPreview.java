package org.github.flowify.catalog.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
public class NodeInputPreview {

    private final String dataType;
    private final String label;
    private final String sourceNodeId;
    private final String sourceNodeLabel;
    private final SchemaPreviewResponse schema;
}
