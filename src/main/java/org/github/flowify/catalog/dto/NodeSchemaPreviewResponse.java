package org.github.flowify.catalog.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
public class NodeSchemaPreviewResponse {

    private String nodeId;
    private SchemaPreviewResponse input;
    private SchemaPreviewResponse output;
}
