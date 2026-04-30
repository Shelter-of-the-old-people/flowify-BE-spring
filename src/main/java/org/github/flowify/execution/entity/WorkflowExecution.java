package org.github.flowify.execution.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "workflow_executions")
public class WorkflowExecution {

    @Id
    private String id;

    @Indexed
    private String workflowId;

    @Indexed
    private String userId;

    private String state;

    @Builder.Default
    private List<NodeLog> nodeLogs = new ArrayList<>();

    private String error;

    private Map<String, Object> output;

    private Long durationMs;

    private Instant startedAt;

    private Instant finishedAt;
}