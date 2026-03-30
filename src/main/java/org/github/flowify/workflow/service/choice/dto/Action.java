package org.github.flowify.workflow.service.choice.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.Map;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Action {

    private String id;
    private String label;

    @JsonProperty("node_type")
    private String nodeType;

    @JsonProperty("output_data_type")
    private String outputDataType;

    private int priority;
    private String description;

    @JsonProperty("applicable_when")
    private Map<String, Object> applicableWhen;

    @JsonProperty("follow_up")
    private FollowUp followUp;

    @JsonProperty("branch_config")
    private BranchConfig branchConfig;
}
