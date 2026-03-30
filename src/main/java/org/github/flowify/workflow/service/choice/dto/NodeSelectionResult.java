package org.github.flowify.workflow.service.choice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NodeSelectionResult {

    private String nodeType;
    private String outputDataType;
    private FollowUp followUp;
    private BranchConfig branchConfig;
}
