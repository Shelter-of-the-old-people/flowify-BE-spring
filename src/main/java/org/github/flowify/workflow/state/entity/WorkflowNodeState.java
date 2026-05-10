package org.github.flowify.workflow.state.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "workflow_node_states")
@CompoundIndex(name = "workflow_node_unique", def = "{'workflowId': 1, 'nodeId': 1}", unique = true)
public class WorkflowNodeState {

    @Id
    private String id;

    private String workflowId;

    private String nodeId;

    private String service;

    @Builder.Default
    private Map<String, Object> state = new LinkedHashMap<>();

    @CreatedDate
    private Instant createdAt;

    @LastModifiedDate
    private Instant updatedAt;
}
