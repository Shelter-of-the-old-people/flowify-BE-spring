package org.github.flowify.workflow.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
@AllArgsConstructor
public class WorkflowGenerationClarificationResponse {

    private final String introMessage;
    private final List<WorkflowGenerationClarificationQuestionResponse> questions;
}
