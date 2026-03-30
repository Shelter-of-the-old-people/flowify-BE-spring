package org.github.flowify.workflow.service.choice.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Option {

    private String id;
    private String label;

    @JsonProperty("node_type")
    private String nodeType;

    @JsonProperty("output_data_type")
    private String outputDataType;

    private Integer priority;
    private String type;
}
