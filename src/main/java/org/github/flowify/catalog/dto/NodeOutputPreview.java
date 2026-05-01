package org.github.flowify.catalog.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
public class NodeOutputPreview {

    private final String dataType;
    private final String label;
    private final SchemaPreviewResponse schema;
}
