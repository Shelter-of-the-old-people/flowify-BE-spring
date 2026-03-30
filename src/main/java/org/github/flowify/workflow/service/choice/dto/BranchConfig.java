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
public class BranchConfig {

    private String question;
    private List<Option> options;

    @JsonProperty("options_source")
    private String optionsSource;

    @JsonProperty("multi_select")
    private Boolean multiSelect;

    private String description;
}
