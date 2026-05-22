package org.github.flowify.workflow.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
@AllArgsConstructor
public class WorkflowGenerationClarificationQuestionResponse {

    private final String id;
    private final String question;
    private final String type;
    private final List<String> options;
}
