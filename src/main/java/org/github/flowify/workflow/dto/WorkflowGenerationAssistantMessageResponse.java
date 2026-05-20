package org.github.flowify.workflow.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
@AllArgsConstructor
public class WorkflowGenerationAssistantMessageResponse {

    private final WorkflowGenerationAssistantMessageType type;
    private final String title;
    private final String content;
    private final List<String> items;
}
