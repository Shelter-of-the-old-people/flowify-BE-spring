package org.github.flowify.workflow.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "workflows")
public class Workflow {

    @Id
    private String id;

    private String name;

    private String description;

    @Indexed
    private String userId;

    @Builder.Default
    private List<String> sharedWith = new ArrayList<>();

    @Builder.Default
    private boolean isTemplate = false;

    private String templateId;

    @Builder.Default
    private List<NodeDefinition> nodes = new ArrayList<>();

    @Builder.Default
    private List<EdgeDefinition> edges = new ArrayList<>();

    private TriggerConfig trigger;

    @Builder.Default
    private boolean isActive = true;

    private String latestExecutionId;

    private String latestExecutionState;

    private Instant latestExecutionStartedAt;

    private Instant latestExecutionFinishedAt;

    @CreatedDate
    private Instant createdAt;

    @LastModifiedDate
    private Instant updatedAt;
}
