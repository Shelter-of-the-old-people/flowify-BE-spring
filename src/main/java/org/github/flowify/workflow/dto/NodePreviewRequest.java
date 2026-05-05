package org.github.flowify.workflow.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class NodePreviewRequest {

    private Integer limit;
    private Boolean includeContent;
}
