package org.github.flowify.catalog.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AiPromptMetadata {

    private final boolean available;
    private final String mode;
    private final String promptSource;
    private final String customPromptMode;
    private final String choiceActionId;
    private final String basePromptSummary;
    private final List<String> includedInstructions;
}
