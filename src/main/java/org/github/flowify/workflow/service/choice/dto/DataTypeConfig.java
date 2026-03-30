package org.github.flowify.workflow.service.choice.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DataTypeConfig {

    private String label;
    private String description;

    @JsonProperty("requires_processing_method")
    private boolean requiresProcessingMethod;

    @JsonProperty("processing_method")
    private ProcessingMethod processingMethod;

    private List<Action> actions;
}
