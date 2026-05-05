package org.github.flowify.workflow.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class NodePreviewRequest {

    private Integer limit;
    private Boolean includeContent;
}
