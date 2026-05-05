package org.github.flowify.workflow.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.util.List;
import java.util.Map;

@Getter
@Builder
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class NodePreviewResponse {

    private final String workflowId;
    private final String nodeId;
    private final String status;
    private final boolean available;
    private final String reason;
    private final Object inputData;
    private final Object outputData;
    private final Object previewData;
    private final List<String> missingFields;
    private final Map<String, Object> metadata;
}
