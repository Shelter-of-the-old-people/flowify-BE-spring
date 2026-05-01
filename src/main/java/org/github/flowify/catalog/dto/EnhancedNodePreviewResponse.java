package org.github.flowify.catalog.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
public class EnhancedNodePreviewResponse {

    private final String nodeId;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private final NodeInputPreview input;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private final NodeOutputPreview output;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private final SourceConfigSummary source;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private final NodeStatusSummary nodeStatus;
}
