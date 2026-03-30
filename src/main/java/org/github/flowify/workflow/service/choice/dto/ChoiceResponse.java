package org.github.flowify.workflow.service.choice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChoiceResponse {

    private String question;
    private List<Option> options;
    private boolean requiresProcessingMethod;
    private Boolean multiSelect;
}
