package org.github.flowify.template.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.github.flowify.workflow.entity.EdgeDefinition;
import org.github.flowify.workflow.entity.NodeDefinition;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
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
@Document(collection = "templates")
public class Template {

    @Id
    private String id;

    private String name;

    private String description;

    @Indexed
    private String category;

    @Indexed
    private String folderKey;

    private String icon;

    @Builder.Default
    private List<NodeDefinition> nodes = new ArrayList<>();

    @Builder.Default
    private List<EdgeDefinition> edges = new ArrayList<>();

    @Builder.Default
    private List<String> requiredServices = new ArrayList<>();

    @Indexed
    @Builder.Default
    private boolean isSystem = false;

    private String authorId;

    @Builder.Default
    private int useCount = 0;

    @CreatedDate
    private Instant createdAt;
}
